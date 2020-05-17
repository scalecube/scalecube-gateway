package io.scalecube.services.testservice;

import io.netty.buffer.ByteBuf;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Authenticator;
import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.GatewaySessionHandler;
import java.util.Optional;
import reactor.util.context.Context;

public class GatewaySessionHandlerImpl implements GatewaySessionHandler {

  private final AuthRegistry authRegistry;

  public GatewaySessionHandlerImpl(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public Context onRequest(GatewaySession session, ByteBuf byteBuf, Context context) {
    Optional<String> authData = authRegistry.getAuth(session.sessionId());
    return authData.map(s -> context.put(Authenticator.AUTH_CONTEXT_KEY, s)).orElse(context);
  }

  @Override
  public ServiceMessage mapMessage(
      GatewaySession session, ServiceMessage message, Context context) {
    return ServiceMessage.from(message)
        .header(AuthRegistry.SESSION_ID, session.sessionId())
        .build();
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
