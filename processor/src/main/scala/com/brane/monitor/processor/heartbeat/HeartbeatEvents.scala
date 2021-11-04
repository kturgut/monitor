package com.brane.monitor.processor.heartbeat

import java.util.concurrent.Future

import com.brane.monitor.config.HeartbeatProcessorSettings


object HeartbeatEvents {

  def init(system: ActorSystem[_], settings: HeartbeatProcessorSettings): Future[ActorRef[Command]] = {
    import system.executionContext
    KafkaClusterSharding(settings.system).messageExtractorNoEnvelope(
      timeout = settings.clusterShardingTimeout,
      topic = settings.topics.head,
      entityIdExtractor = (msg: Command) => msg.entityId,
      settings = settings.kafkaConsumerSettings()
    ).map(messageExtractor => {
      system.log.info("Message extractor created. Initializing sharding")
      ClusterSharding(system).init(
        Entity(settings.entityTypeKey)(createBehavior = _ => HeartbeatEvents())
          .withAllocationStrategy(new ExternalShardAllocationStrategy(system, settings.entityTypeKey.name))
          .withMessageExtractor(messageExtractor))
    })
  }

  sealed trait Command  extends CborSerializable {
     def entityId: String // used for distributing the work to Shard Entities
  }

  final case class LivePassHeartbeatReceived(entityId:String,
                                             partitionId: Int,
                                             heartbeat:SystemHeartbeat,
                                             replyTo: ActorRef[Done]) extends Command
  final case class VrlHeartbeatReceived(entityId:String,
                                        partitionId: Int,
                                        heartbeat:StreamingHeartbeat,
                                        replyTo: ActorRef[Done]) extends Command

  final case class GetMaximumEventTimeObserved(entityId:String, scopeId:String, replyTo: ActorRef[State]) extends Command
  final case class GetMaximumEventDelay(entityId:String, scopeId:String, replyTo: ActorRef[State]) extends Command
  final case class GetTotalEventsCount(entityId:String, scopeId:String, replyTo: ActorRef[State]) extends Command
// WaterMarkEscapeCount
  // Druid team needs number of session summaries per customer per minute
  // HeartbeatCountBySessionId 13.5 million
  // Any extraction beyond gateway time.. we should not
  // Heartbeat delay for that minute. Heartbeat count
  // Aggregate at the minute level -> potentially spark job
  // Kafka dropping 4 percent of its heartbeats? Kafka broker was visible in forum. had partial hardware failure
  // Event time = the time it arrived at gateway
  //
  // Processing time = time it took in gateway
  // WSG = WebServicesGateway: transforms from json. it joins that data with other data
  // gets the IP address from the headers. GeoLookup service to get the IP address
  // It generates clientIds.
  // Central repo/lpg (live pass gateway)
  // processing time Get it from the deserialized heartbeat from rawLog
  // EventTime is the gateway time
  // We could also monitor the delay from the device to here.
  // Statistics: keyedby (Gateway time second + partitionId)
  //     metrics:
  //       1- count additionally keyed by processing time (at system time)
                     // over a minute: two dimensional array of gateway time and system time. That gives us distribution of the delay
  //                 // total count by gateway time
                     // bucketed by gateway time what is the distribution of end to end delays for this partition
                     // if there are too many in the bucket and processing time is too high.. then we can go back to each hop and we find out where the long delays originated
                     // we can compute watermark stuff from this data structure.
                     // Data Structure:   // heartbeats partitionedBy thisHopId, partitionId, systemTimeInSecondsHere, gatewayTime
                     // bucket by second and count
                     // average: sum within the "second partition"
  //       2- system processingTime - previousHop (before it hit Mirror Maker) - need breadcrumbs inside Mirror maker
                     // heartbeats partitionedBy thisHopId, prevHopId, partitionId, gatewayTime, deltaProcessingTimeBetweenHops
  //       3- compute watermark
  // GateWay (serializes) -> LogRelay (same machine as Gateway, writes to Kafka) -> Kafka in RDC ->
  //     ... MirrorMaker in ADC -> Kafka -> MirrorMaker in ADC -> Kafka ->  ...
  // Aggregation is happening in "MirrorMaker in ADC"..
  // There is a kafka cluster in the RDC. There is a mirrorMaker in ADC that reads from that broker
  // Grafana for realtime visualization
  // --> Looker for analytics visualizaions...

  // heatBeats partitionedBy  otherHopId,hop2TimeInSeconds

  // Aggregation
         // Counts bucketed by second.
         // Aggregate: 30 seconds, minutes, 1 hour..
  // Keep track of these counts.
  // every second you write a
  //  case class Hop (hopId:String, hopReceivedTime: Long, hopProcessedTime:Long)
  // state

  final case class HopTimeSlotState(timeSlot:Int

                                   )


  final case class State(minEventTimeMls:Long,
                         maxEventTimeMls:Long,
                         totalVrlEventsReceived: Long,
                         totalLivePassEventsReceived: Long,
                         watermarkTime:Long,
                         totalProcessingTime: Long,
                         maxEventDelay: Long) extends CborSerializable {
    def heartbeat(heartbeat: StreamingHeartbeat): State = {
      heartbeat.createdOn
      this.copy(totalVrlEventsReceived = this.totalVrlEventsReceived + 1) // TODO
    }
    def heartbeat(heartbeat: SystemHeartbeat): State = {
      this.copy(
        totalLivePassEventsReceived = this.totalLivePassEventsReceived + 1) // TODO
    }
  }

  def apply(): Behavior[Command] = running(State(0, 0, 0, 0, 0, 0, 0))


  private def running(state: State): Behavior[Command] = {
    Behaviors.setup { ctx =>

      Behaviors.receiveMessage[Command] {
        case VrlHeartbeatReceived (entityId, partitionId, heartbeat, ack) =>
          ctx.log.info("VRL Heartbeat {} received from kafka partition {}", entityId, partitionId)
          ack.tell(Done)
          running(state.heartbeat(heartbeat))

        case LivePassHeartbeatReceived (entityId, partitionId, heartbeat, ack) =>
          ctx.log.info("VRL Heartbeat {} received from kafka partition {}", entityId, partitionId)
          ack.tell(Done)
          running(state.heartbeat(heartbeat))

        case GetMaximumEventTimeObserved (key, scope, replyTo) =>
          ctx.log.info("key {} scope {}, maximum event time {}", key, scope, state.maxEventTimeMls)
          replyTo ! state
          Behaviors.same  // TODO

        case GetMaximumEventDelay(key, scope, replyTo) =>
          ctx.log.info("key {} scope {}, maximum event time {}", key, scope, state.maxEventTimeMls)
          replyTo ! state
          Behaviors.same // TODO

        case GetTotalEventsCount(key, scope, replyTo) =>
          ctx.log.info("key {} total events count", key, state.totalVrlEventsReceived)
          replyTo ! state
          Behaviors.same // TODO
      }
    }
  }
}
