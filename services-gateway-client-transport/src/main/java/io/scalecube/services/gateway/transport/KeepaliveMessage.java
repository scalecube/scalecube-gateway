package io.scalecube.services.gateway.transport;

import io.scalecube.services.api.ServiceMessage;

public class KeepaliveMessage {
  // TODO: Nice to have in "-common"
  public static final String KEEPALIVE_Q = "io.scalecube.services/keepalive";
  public static final ServiceMessage INSTANCE = ServiceMessage.builder().qualifier(KEEPALIVE_Q).build();

}
