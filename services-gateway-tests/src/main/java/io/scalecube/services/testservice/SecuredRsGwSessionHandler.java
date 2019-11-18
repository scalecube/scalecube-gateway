package io.scalecube.services.testservice;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.SessionEventsHandler;

public class SecuredRsGwSessionHandler implements SessionEventsHandler<ServiceMessage> {
  private final AuthRegistry authRegistry;

  public SecuredRsGwSessionHandler(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public ServiceMessage mapMessage(GatewaySession session, ServiceMessage req) {
    return ServiceMessage.from(req).header(AuthRegistry.SESSION_ID, session).build();
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
