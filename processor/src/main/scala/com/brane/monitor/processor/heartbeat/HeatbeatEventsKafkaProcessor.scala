package com.brane.monitor.processor.heartbeat

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.{ActorSystem => TypedActorSystem}
import akka.kafka.cluster.sharding.KafkaClusterSharding
import akka.kafka.scaladsl.Committer
import akka.kafka.scaladsl.Consumer
import akka.kafka.CommitterSettings
import akka.kafka.Subscriptions
import akka.pattern.retry
import com.brane.monitor.{DeserializationError, HeartbeatBytes, HeartbeatType}
import com.brane.monitor.config.HeartbeatProcessorSettings
import com.brane.monitor.processor.heartbeat.HeartbeatEvents.{LivePassHeartbeatReceived, VrlHeartbeatReceived}
import io.prometheus.client.Summary
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Supervision.{resumingDecider, stoppingDecider}

object HeatbeatEventsKafkaProcessor {

  sealed trait Command

  private case class KafkaConsumerStopped(reason: Try[Any]) extends Command


  private val logger = LoggerFactory.getLogger(this.getClass)
  val bytesRead = Summary.build(HeartbeatBytes, "Heartbeat data input").labelNames(HeartbeatType).register()

  def apply(shardRegion: ActorRef[HeartbeatEvents.Command], processorSettings: HeartbeatProcessorSettings): Behavior[Nothing] = {
    Behaviors
      .setup[Command] { ctx =>
        implicit val sys: TypedActorSystem[_] = ctx.system
        val result = startConsumingFromTopic(shardRegion, processorSettings)

        ctx.pipeToSelf(result) {
          result => KafkaConsumerStopped(result)
        }

        Behaviors.receiveMessage[Command] {
          case KafkaConsumerStopped(reason) =>
            ctx.log.info("Consumer stopped {}", reason)
            Behaviors.stopped
        }
      }
      .narrow
  }


  private def startConsumingFromTopic(shardRegion: ActorRef[HeartbeatEvents.Command], processorSettings: HeartbeatProcessorSettings)
                                     (implicit actorSystem: TypedActorSystem[_]): Future[Done] = {

    implicit val ec: ExecutionContext = actorSystem.executionContext
    implicit val scheduler: Scheduler = actorSystem.toClassic.scheduler
    val classic = actorSystem.toClassic

    val rebalanceListener = KafkaClusterSharding(classic).rebalanceListener(processorSettings.entityTypeKey)

    val subscription = Subscriptions
      .topics(processorSettings.topics: _*)
      .withRebalanceListener(rebalanceListener.toClassic)

    Consumer.sourceWithOffsetContext(processorSettings.kafkaConsumerSettings(), subscription)
      // MapAsync and Retries can be replaced by reliable delivery
      .mapAsync(20) { record =>
        logger.info(s"shard entity consumed kafka partition ${record.key().toString}->${record.partition()} offset:${record.offset()} ")
        retry(() =>
          shardRegion.ask[Done](replyTo => {
            logger.info(s"parsed ${parseHeartbeat(record)}")
            parseHeartbeat(record) match {
              case hb:StreamingHeartbeat =>
                logger.info(s"vrl heartbeat parsed ${hb.key.toString()}->${record.partition()}")
                VrlHeartbeatReceived(hb.key.toString(), record.partition(), hb, replyTo)
              case hb:SystemHeartbeat =>
                logger.info(s"livepass heartbeat parsed ${hb.key.toString()}->${record.partition()}")
                LivePassHeartbeatReceived(hb.key.toString(), record.partition(), hb, replyTo)
            }
          })(processorSettings.askTimeout, actorSystem.scheduler),
          processorSettings.attempts,
          processorSettings.delayBetweenAttempts)
      }
      // .withAttributes(supervisionStrategy(resumingDecider))
      .withAttributes(supervisionStrategy(stoppingDecider))
      .runWith(Committer.sinkWithOffsetContext(CommitterSettings(classic)))
  }

  def parseHeartbeat(record: ConsumerRecord[Array[Byte],Array[Byte]]): Heartbeat =
    PacketbrainSupport.deserialize(record.value) match {
      case Success(logWrapper) =>
        bytesRead.labels(logWrapper.header.eventId.toString).observe(record.value.length)
        Heartbeat(logWrapper)
      case Failure(exception) =>
        logger.error("Error deserializing message.", exception)
        bytesRead.labels(DeserializationError).observe(record.value.length)
        throw new UnsupportedOperationException("packetbrain could not de-serialize message:", exception)
    }

}
