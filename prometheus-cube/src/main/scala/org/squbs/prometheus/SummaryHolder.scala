package org.squbs.prometheus
import io.prometheus.client.Summary

object SummaryHolder {
  val bytesRead = Summary.build("heartbeat_bytes", "Heartbeat data input").labelNames("hb_type").register()
}
