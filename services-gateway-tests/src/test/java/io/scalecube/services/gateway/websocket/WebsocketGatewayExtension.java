package io.scalecube.services.gateway.websocket;

import io.scalecube.services.gateway.AbstractGatewayExtension;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.transport.gw.GwClientTransports;

class WebsocketGatewayExtension extends AbstractGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "ws";

  WebsocketGatewayExtension(Object serviceInstance) {
    super(
        serviceInstance,
        opts -> new WebsocketGateway(opts.id(GATEWAY_ALIAS_NAME)),
        GwClientTransports::websocketGwClientTransport);
  }
}
