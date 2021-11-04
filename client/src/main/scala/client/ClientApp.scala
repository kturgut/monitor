package client

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer
import com.brane.monitor.heartbeat.sharding.{HeartbeatServiceClient, HeartbeatStatsRequest}

import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration
import scala.io.StdIn

object ClientApp extends App {
  implicit val system: ActorSystem = ActorSystem("UserClient")
  implicit val mat: Materializer = Materializer.createMaterializer(system)
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8881).withTls(false)
  val client = HeartbeatServiceClient(clientSettings)

  var partitionId = ""
  while (partitionId != ":q") {
    println("Enter partition id or :q to quit")
    partitionId = StdIn.readLine()

    if (partitionId != ":q") {
      try {
        val runningTotal = Await.result(client.heartbeatStats(HeartbeatStatsRequest(partitionId.toInt)), Duration.Inf)
        println(s"Partition ${partitionId} processed ${runningTotal.totalNumberOfHeartbeats} with total processing time of ${runningTotal.totalProcessingTime}p")

      } catch {
        case _: Exception =>
          println(s"enter a valid partitionId: $partitionId")
          None
      }
    }

  }
  println("Exiting")
  system.terminate()
}
