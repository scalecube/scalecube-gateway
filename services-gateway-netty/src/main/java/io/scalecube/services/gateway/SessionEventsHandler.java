package io.scalecube.services.gateway;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.ws.GatewayMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface SessionEventsHandler<SESSION, MESSAGE> {

  Logger LOGGER = LoggerFactory.getLogger(SessionEventsHandler.class);

  SessionEventsHandler<String, GatewayMessage> DEFAULT_WS_INSTANCE = new SessionEventsHandler<String, GatewayMessage>() {
  };
  SessionEventsHandler<String, ServiceMessage> DEFAULT_RS_INSTANCE = new SessionEventsHandler<String, ServiceMessage>() {
  };

  /**
   * Message mapper function.
   *
   * @param session webscoket session (not null)
   * @param req request message (not null)
   * @return message
   */
  default MESSAGE mapMessage(SESSION session, MESSAGE req) {
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
      SESSION session, Throwable throwable, MESSAGE req, MESSAGE resp) {
    LOGGER.error(
        "Exception occurred on session={}, on request: {}, on response: {}, cause:",
        sessionId(session),
        req,
        resp,
        throwable);
  }

  /**
   * On session open handler.
   *
   * @param session websocket session (not null)
   */
  default void onSessionOpen(SESSION session) {
    LOGGER.info("Session opened: " + session);
  }

  /**
   * On session close handler.
   *
   * @param session websocket session (not null)
   */
  default void onSessionClose(SESSION session) {
    LOGGER.info("Session closed: " + session);
  }

  default String sessionId(SESSION session) {
    return "" + session;
  }

}
