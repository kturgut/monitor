package com.brane.monitor.processor.heartbeat

import akka.http.scaladsl.model.DateTime
import com.brane.monitor.processor.heartbeat.Hop.GatewayHop
import com.brane.parser.runtime.LogWrapper
import com.brane.utils.IPbPacket
import com.google.common.hash.Hashing


  case class HeartbeatKey (keyHash:Int, sessionId:Int, customerId: Int) {
    override def toString():String = s"$customerId::$sessionId::$keyHash"
  }

  sealed trait Heartbeat extends HasKey with HasTime {
    def customerId: Int
    def sessionId:Int
    def isVideoSession: Boolean
  }

  sealed trait HasTime {
    def createdOn: Double // processing time on the last hop
    final lazy val receivedOn:DateTime = DateTime.now
  }

  sealed trait HasKey {
    self :Heartbeat =>

    final lazy val key: HeartbeatKey = HeartbeatKey (
      keyHash,
      sessionId,
      customerId
    )

    protected def keyHash:Int

    protected def clientIdHash(clientId: Seq[Long]): Int = {
      Hashing.murmur3_128.newHasher
        .putLong(clientId(0))
        .putLong(clientId(1))
        .putLong(clientId(2))
        .putLong(clientId(3))
        .hash
        .asInt
    }
  }

  case class StreamingHeartbeat(customerId: Int,
                                requestGroupInfoIdOption: Option[Long],
                                clientIdOption: Option[Seq[Long]],
                                urlType: UrlType,
                                sessionId: Int,
                                createdOn: Double, // gatewayTime
                                processedBy: Hop = GatewayHop,
                                previousHop: Option[Hop] = None
                         ) extends Heartbeat with HasBreadCrumb {

    require ( requestGroupInfoIdOption.isDefined || clientIdOption.isDefined)

    private def isKeyedByUrlType: Boolean =
      urlType == UrlType.eHostVrl || urlType == UrlType.eQueryVrlNoClientId || urlType == UrlType.eUrlExplicitRequestId

    protected def keyHash:Int = (requestGroupInfoIdOption, clientIdOption) match {
      case (Some(groupInfo), _) if isKeyedByUrlType =>
        Hashing.murmur3_128.newHasher
          .putLong(groupInfo)
          .hash
          .asInt
      case (_, Some(clientId)) => clientIdHash(clientId)
      case _ => throw new UnsupportedOperationException("ClientId and requestGroupId not found")
    }
    override def isVideoSession: Boolean = false
  }

  case class SystemHeartbeat(customerId: Int,
                             clientId: Seq[Long],
                             instanceId: Long,
                             sessionId: Int,
                             newClientIdSession: Boolean,
                             createdOn: Double, // gateway timestamp
                             processedBy: Hop = GatewayHop,
                             previousHop: Option[Hop] = None
                               ) extends Heartbeat  {

    override def isVideoSession: Boolean = true

    override protected def keyHash: Int = clientIdHash(clientId)
  }




  case object Heartbeat {

    private def hopProcessingTime(wrapper:HeartbeatWrapper) = pbhb.timeStampUs / 1000000D

    def apply(wrappedInternalHeartbeat: HeartbeatWrapper): Heartbeat = ???

  }
