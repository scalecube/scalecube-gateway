package io.scalecube.services.gateway.websocket;

import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.ws.WebsocketGateway;

class WebsocketLocalGatewayExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "ws";

  WebsocketLocalGatewayExtension(Object serviceInstance) {
    super(
        serviceInstance,
        opts -> new WebsocketGateway(opts.id(GATEWAY_ALIAS_NAME)),
        GatewayClientTransports::websocketGatewayClientTransport);
  }
}
