package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.gateway.AbstractGatewayExtension;
import io.scalecube.services.gateway.transport.GatewayClientTransports;

class RsocketGatewayExtension extends AbstractGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  RsocketGatewayExtension(Object serviceInstance) {
    super(
        ServiceInfo.fromServiceInstance(serviceInstance).build(),
        opts -> new RSocketGateway(opts.id(GATEWAY_ALIAS_NAME)),
        GatewayClientTransports::rsocketGatewayClientTransport);
  }

  RsocketGatewayExtension(ServiceInfo serviceInfo) {
    super(
        serviceInfo,
        opts -> new RSocketGateway(opts.id(GATEWAY_ALIAS_NAME)),
        GatewayClientTransports::rsocketGatewayClientTransport);
  }
}
