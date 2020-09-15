package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.gateway.AuthRegistry;
import io.scalecube.services.gateway.GatewaySessionHandlerImpl;
import io.scalecube.services.gateway.transport.GatewayClientTransports;

public class RsLocalWithAuthExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  RsLocalWithAuthExtension(Object serviceInstance, AuthRegistry authReg) {
    this(ServiceInfo.fromServiceInstance(serviceInstance).build(), authReg);
  }

  RsLocalWithAuthExtension(ServiceInfo serviceInfo, AuthRegistry authReg) {
    super(
        serviceInfo,
        opts ->
            new RSocketGateway(opts.id(GATEWAY_ALIAS_NAME), new GatewaySessionHandlerImpl(authReg)),
        GatewayClientTransports::rsocketGatewayClientTransport);
  }
}
