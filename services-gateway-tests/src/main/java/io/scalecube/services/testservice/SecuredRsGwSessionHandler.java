package io.scalecube.services.testservice;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.SessionEventHandler;

public class SecuredRsGwSessionHandler implements SessionEventHandler<ServiceMessage> {
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
