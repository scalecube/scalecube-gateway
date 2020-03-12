package io.scalecube.services.gateway;

import io.netty.buffer.ByteBuf;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.ws.GatewayMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.context.Context;

public interface GatewaySessionHandler<M> {

  Logger LOGGER = LoggerFactory.getLogger(GatewaySessionHandler.class);

  GatewaySessionHandler<GatewayMessage> DEFAULT_WS_INSTANCE =
      new GatewaySessionHandler<GatewayMessage>() {};
  GatewaySessionHandler<ServiceMessage> DEFAULT_RS_INSTANCE =
      new GatewaySessionHandler<ServiceMessage>() {};

  /**
   * Message mapper function.
   *
   * @param session webscoket session (not null)
   * @param req request message (not null)
   * @return message
   */
  default M mapMessage(GatewaySession session, M req) {
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
  default Context mapRequest(GatewaySession session, ByteBuf byteBuf, Context context) {
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
