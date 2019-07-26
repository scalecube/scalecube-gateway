package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.transport.rsocket.RSocketGatewayClient;

class RsocketLocalGatewayExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  RsocketLocalGatewayExtension(Object serviceInstance) {
    super(
        serviceInstance,
        opts -> new RSocketGateway(opts.id(GATEWAY_ALIAS_NAME)),
        settings ->
            new RSocketGatewayClient(settings, GatewayClientTransports.RSOCKET_CLIENT_CODEC));
  }
}
