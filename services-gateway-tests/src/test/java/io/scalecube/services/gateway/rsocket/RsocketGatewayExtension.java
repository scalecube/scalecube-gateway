package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.gateway.AbstractGatewayExtension;
import io.scalecube.services.transport.gw.GwTransportBootstraps;

class RsocketGatewayExtension extends AbstractGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  RsocketGatewayExtension(Object serviceInstance) {
    super(serviceInstance, opts -> new RSocketGateway(opts.id(GATEWAY_ALIAS_NAME)));
  }

  @Override
  protected ServiceTransportBootstrap gwClientTransport(ServiceTransportBootstrap op) {
    return GwTransportBootstraps.rsocketGwTransport(clientSettings, op);
  }

  @Override
  protected String gatewayAliasName() {
    return GATEWAY_ALIAS_NAME;
  }
}
