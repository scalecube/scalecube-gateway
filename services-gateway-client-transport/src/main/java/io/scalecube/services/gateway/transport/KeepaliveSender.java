package io.scalecube.services.gateway.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeepaliveSender implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeepaliveSender.class);

  private final GatewayClient client;

  public KeepaliveSender(GatewayClient client) {
    this.client = client;
  }

  @Override
  public void run() {
    LOGGER.info("Sending keepalive due to idle state...");
    client
        .requestResponse(KeepaliveMessage.INSTANCE)
        .subscribe(
            ok -> LOGGER.info("Keepalive response"), ko -> LOGGER.error("Keepalive error ", ko));
  }
}
