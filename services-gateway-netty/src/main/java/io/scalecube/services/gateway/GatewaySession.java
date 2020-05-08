package io.scalecube.services.gateway;

import java.util.List;
import java.util.Map;

public interface GatewaySession {

  /**
   * Session id representation to be unique per client session.
   *
   * @return session id
   */
  long sessionId();

  /**
   * Returns headers associated with session.
   *
   * @return heades map
   */
  Map<String, List<String>> headers();
}
