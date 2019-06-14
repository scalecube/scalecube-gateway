package io.scalecube.services.gateway.websocket;

import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.gateway.AbstractGatewayExtension;
import io.scalecube.services.transport.gw.GwTransportBootstraps;
import io.scalecube.services.gateway.ws.WebsocketGateway;

class WebsocketGatewayExtension extends AbstractGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "ws";

  WebsocketGatewayExtension(Object serviceInstance) {
    super(serviceInstance, opts -> new WebsocketGateway(opts.id(GATEWAY_ALIAS_NAME)));
  }

  @Override
  protected ServiceTransportBootstrap gwClientTransport(ServiceTransportBootstrap op) {
    return GwTransportBootstraps.websocketGwTransport(clientSettings, op);
  }

  @Override
  protected String gatewayAliasName() {
    return GATEWAY_ALIAS_NAME;
  }
}
