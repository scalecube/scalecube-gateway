package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.gateway.AuthRegistry;
import io.scalecube.services.gateway.GatewaySessionHandlerImpl;
import io.scalecube.services.gateway.transport.GatewayClientTransports;

public class RsLocalWithAuthExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  RsLocalWithAuthExtension(Object serviceInstance, AuthRegistry authReg) {
    super(
        ServiceInfo.fromServiceInstance(serviceInstance).build(),
        opts ->
            new RSocketGateway(opts.id(GATEWAY_ALIAS_NAME), new GatewaySessionHandlerImpl(authReg)),
        GatewayClientTransports::rsocketGatewayClientTransport);
  }
}
