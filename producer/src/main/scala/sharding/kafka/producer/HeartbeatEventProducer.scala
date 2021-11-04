package sharding.kafka.producer

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import com.brane.monitor.heartbeat.sharding.kafka.serialization.user_events.StreamingHeartbeatProto
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

object HeartbeatEventProducer extends App {

  implicit val system: ActorSystem = ActorSystem(
    "HeartbeatEventProducer",
    ConfigFactory.parseString("""
      akka.actor.provider = "local" 
     """.stripMargin).withFallback(ConfigFactory.load()).resolve())

  val log = Logging(system, "HeartbeatEventProducer")

  val config = system.settings.config.getConfig("akka.kafka.producer")

  val producerConfig = ProducerConfig(system.settings.config.getConfig("kafka-to-sharding-producer"))

  val producerSettings: ProducerSettings[String, Array[Byte]] =
    ProducerSettings(config, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(producerConfig.bootstrapServers)

  val nrCustomers = 5
  val nrRequestGroups = 7
  val maxNumberOfClientIds = 10
  val maxNumberOfSessionsPerClientId = 3
  val clientIds = (1l to maxNumberOfClientIds).map(a=>  Seq(a, a+1, a+2, a+3))
  val done: Future[Done] =
    Source
      .tick(1.second, 1.second, "tick")
      .map(_ => {

        val randomCustomerId = Random.nextInt(nrCustomers)
        val randomRequestGroupId = Random.nextInt(nrRequestGroups)
        val randomUrlType = VrlHeartbeatProto.UrlType.fromValue(Random.nextInt(4))
        val randomCreationTime = (System.currentTimeMillis() - Random.nextInt(100)).toDouble
        val randomClientId = clientIds(Random.nextInt(maxNumberOfClientIds))
        val gatewayHop = VrlHeartbeatProto.Hop.Gateway
        val message = {
          if (randomClientId(0) % 3 == 0) {
            log.info("Sending vrlHeartbeat from customer {} clientId {}", randomCustomerId, randomClientId)
            VrlHeartbeatProto(randomCustomerId, 0L ,randomClientId,randomUrlType,randomCreationTime, gatewayHop).toByteArray
          }
          else {
            log.info("Sending vrlHeartbeat from customer {} requestGroupId {}", randomCustomerId, randomRequestGroupId)
            VrlHeartbeatProto(randomCustomerId, randomRequestGroupId, Seq.empty,randomUrlType,randomCreationTime, gatewayHop).toByteArray
          }
        }
        val randomSessionId = Random.nextInt(maxNumberOfSessionsPerClientId)
        val heartbeatKey = s"$randomCustomerId::$randomSessionId::${randomClientId(0)}" // TODO use same keyhash
        // rely on the default kafka partitioner to hash the key and distribute among shards
        // the logic of the default partitioner must be replicated in MessageExtractor entityId -> shardId function
        new ProducerRecord[String, Array[Byte]](producerConfig.topic, heartbeatKey, message)
      })
      .runWith(Producer.plainSink(producerSettings))


}
