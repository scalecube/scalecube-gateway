package io.scalecube.services.gateway;

import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.transport.api.ServiceTransport;
import io.scalecube.services.transport.gw.GwServiceTransports;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.services.transport.rsocket.RSocketTransportResources;

public class ServiceTransports {

  private ServiceTransports() {
    // Do not instantiate
  }

  public static ServiceTransportBootstrap rsocketServiceTransport(ServiceTransportBootstrap opts) {
    return serviceBootstrap(opts, RSocketServiceTransport.INSTANCE);
  }

  public static ServiceTransportBootstrap rsocketGwTransport(GwClientSettings cs,
      ServiceTransportBootstrap opts) {
    ServiceTransport sTransport = GwServiceTransports.INSTANCE.rsocketGwServiceTransport(cs);
    return serviceBootstrap(opts, sTransport);
  }

  public static ServiceTransportBootstrap websocketGwTransport(GwClientSettings cs,
      ServiceTransportBootstrap opts) {
    ServiceTransport sTransport = GwServiceTransports.INSTANCE.websocketGwServiceTransport(cs);
    return serviceBootstrap(opts, sTransport);
  }

  public static ServiceTransportBootstrap httpGwTransport(GwClientSettings cs,
      ServiceTransportBootstrap opts) {
    ServiceTransport sTransport = GwServiceTransports.INSTANCE.httpGwServiceTransport(cs);
    return serviceBootstrap(opts, sTransport);
  }


  private static ServiceTransportBootstrap serviceBootstrap(ServiceTransportBootstrap opts,
      ServiceTransport sTransport) {
    return opts.resources(RSocketTransportResources::new)
        .client(sTransport::clientTransport)
        .server(sTransport::serverTransport);
  }
}
