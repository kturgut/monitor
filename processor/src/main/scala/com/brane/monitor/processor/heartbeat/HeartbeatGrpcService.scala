package com.brane.monitor.processor.heartbeat

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.util.Timeout
import HeartbeatEvents.{Command, GetMaximumEventDelay, State}
import com.brane.monitor.heartbeat.sharding.{HeartbeatStatsRequest, HeartbeatStatsResponse}
import com.brane.monitor.heartbeat.sharding.HeartbeatService

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

class HeartbeatGrpcService(system: ActorSystem[_], shardRegion: ActorRef[Command]) extends HeartbeatService {

  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val sched: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext

  override def heartbeatStats(in: HeartbeatStatsRequest): Future[HeartbeatStatsResponse] = {
    shardRegion
      .ask[State](replyTo => GetMaximumEventDelay(in.partitionId.toString, "some scope", replyTo)) // TODO
      .map(runningTotal => HeartbeatStatsResponse(in.partitionId,
        runningTotal.maxEventTimeMls,
        runningTotal.watermarkTime,
        runningTotal.totalProcessingTime,
        runningTotal.maxEventDelay))
  }
}
