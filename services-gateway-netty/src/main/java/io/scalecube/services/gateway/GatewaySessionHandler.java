package io.scalecube.services.gateway;

import io.netty.buffer.ByteBuf;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.ws.GatewayMessage;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public interface GatewaySessionHandler<M> {

  Logger LOGGER = LoggerFactory.getLogger(GatewaySessionHandler.class);

  GatewaySessionHandler<GatewayMessage> DEFAULT_WS_INSTANCE = new GatewaySessionHandler<>() {};
  GatewaySessionHandler<ServiceMessage> DEFAULT_RS_INSTANCE = new GatewaySessionHandler<>() {};

  /**
   * Message mapper function.
   *
   * @param session webscoket session (not null)
   * @param req request message (not null)
   * @return message
   */
  default M mapMessage(GatewaySession session, M req, Context context) {
    return req;
  }

  /**
   * Request mapper function.
   *
   * @param session session
   * @param byteBuf request buffer
   * @param context subscriber context
   * @return context
   */
  default Context onRequest(GatewaySession session, ByteBuf byteBuf, Context context) {
    return context;
  }

  /**
   * On response handler.
   *
   * @param session session
   * @param byteBuf response buffer
   * @param message response message
   * @param context subscriber context
   */
  default void onResponse(
      GatewaySession session, ByteBuf byteBuf, GatewayMessage message, Context context) {
    // no-op
  }

  /**
   * Error handler function.
   *
   * @param session webscoket session (not null)
   * @param throwable an exception that occurred (not null)
   * @param context subscriber context
   */
  default void onError(GatewaySession session, Throwable throwable, Context context) {
    LOGGER.error(
        "Exception occurred on session: {}, on context: {}, cause:",
        session.sessionId(),
        context,
        throwable);
  }

  /**
   * On session error.
   *
   * @param session webscoket session (not null)
   * @param throwable an exception that occurred (not null)
   */
  default void onSessionError(GatewaySession session, Throwable throwable) {
    LOGGER.error("Exception occurred on session: {}, cause:", session.sessionId(), throwable);
  }

  default Mono<Void> onConnectionOpen(Map<String, List<String>> headers) {
    return Mono.fromRunnable(() -> LOGGER.debug("Connection opened, headers({})", headers.size()));
  }

  /**
   * On session open handler.
   *
   * @param session websocket session (not null)
   */
  default void onSessionOpen(GatewaySession session) {
    LOGGER.info("Session opened: {}", session);
  }

  /**
   * On session close handler.
   *
   * @param session websocket session (not null)
   */
  default void onSessionClose(GatewaySession session) {
    LOGGER.info("Session closed: {}", session);
  }
}
