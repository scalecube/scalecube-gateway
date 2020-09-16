package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.gateway.GatewayOptions;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import java.util.function.Function;

class RsocketLocalGatewayExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  RsocketLocalGatewayExtension(Object serviceInstance) {
    this(ServiceInfo.fromServiceInstance(serviceInstance).build());
  }

  RsocketLocalGatewayExtension(ServiceInfo serviceInfo) {
    this(serviceInfo, RSocketGateway::new);
  }

  RsocketLocalGatewayExtension(
      ServiceInfo serviceInfo, Function<GatewayOptions, RSocketGateway> gatewaySupplier) {
    super(
        serviceInfo,
        opts -> gatewaySupplier.apply(opts.id(GATEWAY_ALIAS_NAME)),
        GatewayClientTransports::rsocketGatewayClientTransport);
  }
}
