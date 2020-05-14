package io.scalecube.services.testservice;

import io.netty.buffer.ByteBuf;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Authenticator;
import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.GatewaySessionHandler;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.context.Context;

public class RSocketGatewaySessionHandler implements GatewaySessionHandler<ServiceMessage> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGatewaySessionHandler.class);

  private final AuthRegistry authRegistry;

  public RSocketGatewaySessionHandler(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public Context onRequest(GatewaySession session, ByteBuf byteBuf, Context context) {
    Optional<String> authData = authRegistry.getAuth(session.sessionId());
    if (authData.isEmpty()) {
      return context;
    }
    return Context.of(Authenticator.AUTH_CONTEXT_KEY, authData.get());
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
