package io.scalecube.services.gateway;

import java.util.concurrent.CountDownLatch;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class TestGatewaySessionHandler implements GatewaySessionHandler {

  public final CountDownLatch msgLatch = new CountDownLatch(1);
  public final CountDownLatch connLatch = new CountDownLatch(1);
  public final CountDownLatch disconnLatch = new CountDownLatch(1);

  @Override
  public Object mapMessage(GatewaySession s, Object req, Context context) {
    msgLatch.countDown();
    return req;
  }

  @Override
  public Mono<Void> onSessionOpen(GatewaySession s) {
    return Mono.fromRunnable(connLatch::countDown);
  }

  @Override
  public Mono<Void> onSessionClose(GatewaySession s) {
    return Mono.fromRunnable(disconnLatch::countDown);
  }
}
