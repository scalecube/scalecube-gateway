package io.scalecube.services.testservice;

import io.scalecube.services.gateway.SessionEventsHandler;
import io.scalecube.services.gateway.ws.GatewayMessage;

public class SecuredWsGwSessionManager implements SessionEventsHandler<String, GatewayMessage> {
  private final AuthRegistry authRegistry;

  public SecuredWsGwSessionManager(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public GatewayMessage mapMessage(String session, GatewayMessage req) {
    return GatewayMessage.from(req).header(AuthRegistry.SESSION_ID, sessionId(session)).build();
  }

  @Override
  public void onSessionOpen(String s) {
    System.out.println("Session opened: " + sessionId(s));
  }

  @Override
  public void onSessionClose(String session) {
    System.out.println("Session removed:" + session);
    authRegistry.removeAuth(session);
  }
}
