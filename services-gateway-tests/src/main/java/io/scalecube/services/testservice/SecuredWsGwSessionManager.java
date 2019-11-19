package io.scalecube.services.testservice;

import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.SessionEventHandler;
import io.scalecube.services.gateway.ws.GatewayMessage;

public class SecuredWsGwSessionManager implements SessionEventHandler<GatewayMessage> {
  private final AuthRegistry authRegistry;

  public SecuredWsGwSessionManager(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public GatewayMessage mapMessage(GatewaySession session, GatewayMessage req) {
    return GatewayMessage.from(req).header(AuthRegistry.SESSION_ID, session).build();
  }

  @Override
  public void onSessionOpen(GatewaySession s) {
    System.out.println("Session opened: " + s);
  }

  @Override
  public void onSessionClose(GatewaySession session) {
    System.out.println("Session removed:" + session);
    authRegistry.removeAuth(session.sessionId());
  }
}
