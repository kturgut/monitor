package com.brane.monitor.config

import akka.actor.ActorSystem
import com.typesafe.config.Config

object MonitorConfig {

  val MonitorActorSystem = "Monitor"
  val KafkaToShardingProcessor = "kafka-heartbeat-processor"
  val BootstrapServers = "bootstrap-servers"
  val ShardRegion = "kafka-event-processor"
  val Topics = "topics"
  val Group = "group"
  val AskTimeout = "ask-timeout"
  val StopTimeout = "stop-timeout"
  val ClusterShardingTimeout = "cluster-sharding-timeout"
  val NumberOfAttempts = "attempts"
  val DelayBetweenAttempts = "delay-between-attempts"

  @volatile private var envMap = Map.empty[String, Config]

  /**
   * Creates the correct configuration for a particular environment and falls back to base configuration.
   * The environment is provided as a configuration property `runtime.environment`, which is generally passed as
   * a `-Druntime.environment=${env-name}` argument to the command line starting this process.
   *
   * @param system The [[ActorSystem]].
   * @return The derived configuration based on the environment.
   */
  def envConfig(implicit system: ActorSystem): Config = {
    envMap.get(system.name) match {
      case Some(config) => config
      case None =>
        val baseConfig = system.settings.config
        val config = getStringOption("runtime.environment", baseConfig) match {
          case Some(env) =>
            system.log.info(s"runtime.environment override is detected, using:$env instead}")
            baseConfig.getConfig(env).withFallback(baseConfig)
          case None => baseConfig
        }
        envMap += system.name -> config
        config
    }
  }

  protected def getStringOption(path: String, primary: Config): Option[String] =
    (primary :: Nil).find(_.hasPath(path)).map(a => Some(a.getString(path))).getOrElse(None)


}
