package io.scalecube.services.gateway;

public interface GatewaySession {

  /**
   * Session id representation to be unique per client session.
   *
   * @return session id
   */
  long sessionId();
}
