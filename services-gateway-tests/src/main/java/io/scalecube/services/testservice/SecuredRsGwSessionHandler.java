package io.scalecube.services.testservice;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.SessionEventsHandler;

public class SecuredRsGwSessionHandler implements SessionEventsHandler<String, ServiceMessage> {
  private final AuthRegistry authRegistry;

  public SecuredRsGwSessionHandler(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public ServiceMessage mapMessage(String session, ServiceMessage req) {
    return ServiceMessage.from(req).header(AuthRegistry.SESSION_ID, sessionId(session)).build();
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
