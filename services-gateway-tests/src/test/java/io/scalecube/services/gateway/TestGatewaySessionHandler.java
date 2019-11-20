package io.scalecube.services.gateway;

import java.util.concurrent.CountDownLatch;

public class TestGatewaySessionHandler implements GatewaySessionHandler {

  public final CountDownLatch msgLatch = new CountDownLatch(1);
  public final CountDownLatch connLatch = new CountDownLatch(1);
  public final CountDownLatch disconnLatch = new CountDownLatch(1);

  @Override
  public Object mapMessage(GatewaySession s, Object req) {
    msgLatch.countDown();
    return req;
  }

  @Override
  public void onSessionOpen(GatewaySession s) {
    connLatch.countDown();
  }

  @Override
  public void onSessionClose(GatewaySession s) {
    disconnLatch.countDown();
  }
}
