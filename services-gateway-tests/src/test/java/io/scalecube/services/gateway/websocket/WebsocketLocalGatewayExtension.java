package io.scalecube.services.gateway.websocket;

import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.transport.gw.GwClientTransports;

class WebsocketLocalGatewayExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "ws";

  WebsocketLocalGatewayExtension(Object serviceInstance) {
    super(
        serviceInstance,
        opts -> new WebsocketGateway(opts.id(GATEWAY_ALIAS_NAME)),
        GwClientTransports::websocketGwClientTransport);
  }
}
