package com.brane.monitor.config

import com.brane.monitor.processor.heartbeat.HeartbeatEvents.Command
import com.brane.monitor.config.MonitorConfig._
import com.brane.monitor.processor.heartbeat.HeartbeatEvents
import org.apache.kafka.clients.consumer.{ConsumerConfig, OffsetResetStrategy}
import org.apache.kafka.common.serialization.ByteArrayDeserializer

case object HeartbeatProcessorSettings {
  def apply(configLocation: String, system: ActorSystem): HeartbeatProcessorSettings = {
    val config = envConfig(system).getConfig(configLocation)
    new HeartbeatProcessorSettings(
      config.getString(BootstrapServers),
      config.getStringList(Topics).asScala.toList,
      config.getString(Group),
      Timeout.create(config.getDuration(AskTimeout)),
      config.getDuration(StopTimeout).toScala,
      config.getDuration(ClusterShardingTimeout).toScala,
      config.getInt(NumberOfAttempts),
      config.getDuration(DelayBetweenAttempts).toScala,
      system: ActorSystem
    )
  }
}

final class HeartbeatProcessorSettings(val bootstrapServers: String,
                                       val topics: List[String],
                                       val groupId: String,
                                       val askTimeout: Timeout,
                                       val stopTimeout: FiniteDuration,
                                       val clusterShardingTimeout: FiniteDuration,
                                       val attempts: Int,
                                       val delayBetweenAttempts: FiniteDuration,
                                       val system: ActorSystem) {

  system.log.info(s"Heartbeat processing settings are bootstrapServers:{} topics:{} groupId:{}", bootstrapServers, topics.mkString(","), groupId)

  def kafkaConsumerSettings(): ConsumerSettings[Array[Byte], Array[Byte]] = {
    ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(groupId)
//      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
      .withStopTimeout(stopTimeout)
  }

  /**
   * By using the same consumer group id as our entity type key name we can setup multiple consumer groups and connect
   * each with a different sharded entity coordinator.
   */
  val entityTypeKey: EntityTypeKey[Command] = EntityTypeKey(groupId)
}
