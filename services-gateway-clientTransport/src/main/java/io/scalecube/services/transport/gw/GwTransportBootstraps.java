package io.scalecube.services.transport.gw;

import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.transport.api.ServiceTransport;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.services.transport.rsocket.RSocketTransportResources;

public class GwTransportBootstraps {

  private GwTransportBootstraps() {
    // Do not instantiate
  }

  public static ServiceTransportBootstrap rsocketServiceTransport(ServiceTransportBootstrap opts) {
    return serviceBootstrap(opts, RSocketServiceTransport.INSTANCE);
  }

  public static ServiceTransportBootstrap rsocketGwTransport(
      GwClientSettings cs, ServiceTransportBootstrap opts) {
    ServiceTransport transport = GwServiceTransports.INSTANCE.rsocketGwServiceTransport(cs);
    return serviceBootstrap(opts, transport);
  }

  public static ServiceTransportBootstrap websocketGwTransport(
      GwClientSettings cs, ServiceTransportBootstrap opts) {
    ServiceTransport transport = GwServiceTransports.INSTANCE.websocketGwServiceTransport(cs);
    return serviceBootstrap(opts, transport);
  }

  public static ServiceTransportBootstrap httpGwTransport(
      GwClientSettings cs, ServiceTransportBootstrap opts) {
    ServiceTransport transport = GwServiceTransports.INSTANCE.httpGwServiceTransport(cs);
    return serviceBootstrap(opts, transport);
  }

  private static ServiceTransportBootstrap serviceBootstrap(
      ServiceTransportBootstrap opts, ServiceTransport transport) {
    return opts.resources(RSocketTransportResources::new)
        .client(transport::clientTransport)
        .server(transport::serverTransport);
  }
}
