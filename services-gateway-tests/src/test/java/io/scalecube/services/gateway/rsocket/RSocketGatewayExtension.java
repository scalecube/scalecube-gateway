package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.gateway.AbstractGatewayExtension;
import io.scalecube.services.gateway.transport.GatewayClientTransports;

class RSocketGatewayExtension extends AbstractGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  RSocketGatewayExtension(Object serviceInstance) {
    this(ServiceInfo.fromServiceInstance(serviceInstance).build());
  }

  RSocketGatewayExtension(ServiceInfo serviceInfo) {
    super(
        serviceInfo,
        opts -> new RSocketGateway(opts.id(GATEWAY_ALIAS_NAME)),
        GatewayClientTransports::rsocketGatewayClientTransport);
  }
}
