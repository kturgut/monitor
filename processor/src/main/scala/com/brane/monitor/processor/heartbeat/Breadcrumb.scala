package com.brane.monitor.processor.heartbeat

import com.brane.monitor.processor.heartbeat.Hop.HopId

sealed case class Hop(id:HopId, name:String, dependsOn:Seq[Hop]=Nil)

object Hop {

  type HopId = Int

  val UnknownHop = new Hop(0,"Unknown Hop")
  val GatewayHop = new Hop(1,"Gateway Hop")
  val LogRelayHop = new Hop(2, "LogRelay Hop")
  val RDCMirrorMakerHop = new Hop(3, "RDC Custom MirrorMaker Hop")
  val ADCMirrorMakerHop = new Hop(4, "ADC Custom MirrorMaker Hop")

  lazy val Hops = GatewayHop :: LogRelayHop :: RDCMirrorMakerHop :: ADCMirrorMakerHop :: Nil

  lazy val HopMap = Hops.map(hop=> hop.id -> hop).toMap

}

trait HasBreadCrumb {
  self :Heartbeat with HasTime =>
  def processedBy: Hop
  def previousHop: Option[Hop]
}