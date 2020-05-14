package io.scalecube.services.testservice;

import io.netty.buffer.ByteBuf;
import io.scalecube.services.auth.Authenticator;
import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.GatewaySessionHandler;
import io.scalecube.services.gateway.ws.GatewayMessage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.context.Context;

public class WebsocketGatewaySessionHandler implements GatewaySessionHandler<GatewayMessage> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(WebsocketGatewaySessionHandler.class);

  private final AuthRegistry authRegistry;

  public WebsocketGatewaySessionHandler(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public Context onRequest(GatewaySession session, ByteBuf byteBuf, Context context) {
    Optional<String> authData = authRegistry.getAuth(session.sessionId());
    return authData.map(s -> context.put(Authenticator.AUTH_CONTEXT_KEY, s)).orElse(context);
  }

  @Override
  public GatewayMessage mapMessage(
      GatewaySession session, GatewayMessage message, Context context) {
    return GatewayMessage.from(message)
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
