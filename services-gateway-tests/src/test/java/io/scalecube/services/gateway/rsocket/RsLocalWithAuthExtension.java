package io.scalecube.services.gateway.rsocket;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.auth.Authenticator;
import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.testservice.AuthRegistry;
import io.scalecube.services.testservice.SecuredRsGwGatewaySessionHandler;

public class RsLocalWithAuthExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "rsws";

  public RsLocalWithAuthExtension(
      Object serviceInstance, Authenticator authenticator, AuthRegistry authReg) {
    super(
        ServiceInfo.fromServiceInstance(serviceInstance).authenticator(authenticator).build(),
        opts ->
            new RSocketGateway(
                opts.id(GATEWAY_ALIAS_NAME), new SecuredRsGwGatewaySessionHandler(authReg)),
        GatewayClientTransports::rsocketGatewayClientTransport);
  }
}
