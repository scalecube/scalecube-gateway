package io.scalecube.services.testservice;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.GatewaySessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecuredRsGwGatewaySessionHandler implements GatewaySessionHandler<ServiceMessage> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(SecuredRsGwGatewaySessionHandler.class);
  private final AuthRegistry authRegistry;

  public SecuredRsGwGatewaySessionHandler(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public ServiceMessage mapMessage(GatewaySession session, ServiceMessage req) {
    return ServiceMessage.from(req).header(AuthRegistry.SESSION_ID, session.sessionId()).build();
  }

  @Override
  public void onSessionOpen(GatewaySession s) {
    LOGGER.info("Session opened: {}", s);
  }

  @Override
  public void onSessionClose(GatewaySession session) {
    LOGGER.info("Session removed: {}", session);
    authRegistry.removeAuth(session.sessionId());
  }
}
