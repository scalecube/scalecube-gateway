package io.scalecube.services.testservice;

import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.GatewaySessionHandler;
import io.scalecube.services.gateway.ws.GatewayMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.context.Context;

public class SecuredWsGwGatewaySessionManager implements GatewaySessionHandler<GatewayMessage> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(SecuredWsGwGatewaySessionManager.class);
  private final AuthRegistry authRegistry;

  public SecuredWsGwGatewaySessionManager(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public GatewayMessage mapMessage(GatewaySession session, GatewayMessage req, Context context) {
    return GatewayMessage.from(req).header(AuthRegistry.SESSION_ID, session.sessionId()).build();
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
