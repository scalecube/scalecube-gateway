package io.scalecube.services.gateway;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.ws.GatewayMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface SessionEventsHandler<M> {

  Logger LOGGER = LoggerFactory.getLogger(SessionEventsHandler.class);

  SessionEventsHandler<GatewayMessage> DEFAULT_WS_INSTANCE =
      new SessionEventsHandler<GatewayMessage>() {};
  SessionEventsHandler<ServiceMessage> DEFAULT_RS_INSTANCE =
      new SessionEventsHandler<ServiceMessage>() {};

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
   * Error handler function.
   *
   * @param session webscoket session (not null)
   * @param throwable an exception that occurred (not null)
   * @param req request message (optional)
   * @param resp response message (optional)
   */
  default void onError(GatewaySession session, Throwable throwable, M req, M resp) {
    LOGGER.error(
        "Exception occurred on session={}, on request: {}, on response: {}, cause:",
        session.sessionId(),
        req,
        resp,
        throwable);
  }

  /**
   * On session open handler.
   *
   * @param session websocket session (not null)
   */
  default void onSessionOpen(GatewaySession session) {
    LOGGER.info("Session opened: " + session);
  }

  /**
   * On session close handler.
   *
   * @param session websocket session (not null)
   */
  default void onSessionClose(GatewaySession session) {
    LOGGER.info("Session closed: " + session);
  }
}
