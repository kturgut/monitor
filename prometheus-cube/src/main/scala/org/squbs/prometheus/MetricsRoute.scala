package org.squbs.prometheus

import akka.http.scaladsl.server.Route
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports
import org.squbs.unicomplex.RouteDefinition

class MetricsRoute extends RouteDefinition{

  DefaultExports.initialize()
  private val prometheusRegistry = CollectorRegistry.defaultRegistry
  private val metricsEndpoint = new MetricsEndpoint(prometheusRegistry)

  override def route: Route = metricsEndpoint.routes
}
