package io.scalecube.services.gateway;

import java.util.List;

public interface GatewaySession {

  /**
   * Session id representation to be unique per client session.
   *
   * @return session id
   */
  long sessionId();

  /**
   * Retruns header value (first one).
   *
   * @param name header name
   * @return header value
   */
  String headerValue(String name);

  /**
   * Returns all header values.
   *
   * @param name header name
   * @return all header values
   */
  List<String> headerValues(String name);
}
