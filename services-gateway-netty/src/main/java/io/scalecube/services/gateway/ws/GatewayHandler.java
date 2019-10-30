package io.scalecube.services.gateway.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface GatewayHandler {

  Logger LOGGER = LoggerFactory.getLogger(GatewayHandler.class);

  GatewayHandler DEFAULT_INSTANCE = new GatewayHandler() {};

  /**
   * Message mapper function.
   *
   * @param session webscoket session (not null)
   * @param req request message (not null)
   * @return message
   */
  default GatewayMessage mapMessage(WebsocketSession session, GatewayMessage req) {
    return req;
  }

  /**
   * Error handler function.
   *
   * @param session webscoket session (not null)
   * @param throwable an exception that occurred (not null)
   * @param req request message (optional)
   * @param resp response message (optional)
   */
  default void onError(
      WebsocketSession session, Throwable throwable, GatewayMessage req, GatewayMessage resp) {
    LOGGER.error(
        "Exception occurred on session={}, on request: {}, on response: {}, cause:",
        session.id(),
        req,
        resp,
        throwable);
  }

  /**
   * On session open handler.
   *
   * @param session websocket session (not null)
   */
  default void onSessionOpen(WebsocketSession session) {
    LOGGER.info("Session opened: " + session);
  }

  /**
   * On session close handler.
   *
   * @param session websocket session (not null)
   */
  default void onSessionClose(WebsocketSession session) {
    LOGGER.info("Session closed: " + session);
  }
}
