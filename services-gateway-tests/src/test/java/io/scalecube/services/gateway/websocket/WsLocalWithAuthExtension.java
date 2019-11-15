package io.scalecube.services.gateway.websocket;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.auth.Authenticator;
import io.scalecube.services.gateway.AbstractLocalGatewayExtension;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.testservice.AuthRegistry;
import io.scalecube.services.testservice.SecuredWsGwSessionManager;

public class WsLocalWithAuthExtension extends AbstractLocalGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "ws";

  WsLocalWithAuthExtension(
      Object serviceInstance, Authenticator authenticator, AuthRegistry authReg) {
    super(
        ServiceInfo.fromServiceInstance(serviceInstance).authenticator(authenticator),
        opts ->
            new WebsocketGateway(
                opts.id(GATEWAY_ALIAS_NAME), new SecuredWsGwSessionManager(authReg)),
        GatewayClientTransports::websocketGatewayClientTransport);
  }
}
