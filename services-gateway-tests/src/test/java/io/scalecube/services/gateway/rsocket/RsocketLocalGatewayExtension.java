package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.transport.gw.GwTransportBootstraps;

class RsocketLocalGatewayExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  RsocketLocalGatewayExtension(Object serviceInstance) {
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
