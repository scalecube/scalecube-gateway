package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.exceptions.ForbiddenException;
import io.scalecube.services.gateway.rsocket.RSocketGateway;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.transport.StaticAddressRouter;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

public class GatewayHandlerTest {

  public static final int RS_PORT = 8080;
  public static final int WS_PORT = 7070;
  public static final String RSGW = "rsgw";
  public static final String WSGW = "wsgw";

  @Test
  void testGatewayHandlers() {

    // Given:
    TestHandler rsHandler = new TestHandler();
    TestHandler wsHandler = new TestHandler();
    Function<GatewayOptions, Gateway> rsGw =
        opts -> new RSocketGateway(opts.id(RSGW).port(RS_PORT), rsHandler);
    Function<GatewayOptions, Gateway> wsGw =
        opts -> new RSocketGateway(opts.id(WSGW).port(WS_PORT), wsHandler);
    TestService serviceInstance = new TestServiceImpl();

    // Start cluster with 2 gateways and one service at same node
    Microservices cluster =
        Microservices.builder().services(serviceInstance).gateway(rsGw).gateway(wsGw).startAwait();

    LoopResources clientLoopResources = LoopResources.create("gateway-client-transport-worker");

    Address rsGwAddr = cluster.gateway(RSGW).address();
    ServiceCall rsServiceCall =
        new ServiceCall()
            .transport(
                GatewayClientTransports.rsocketGatewayClientTransport(
                    GatewayClientSettings.builder().address(rsGwAddr).build()))
            .router(new StaticAddressRouter(rsGwAddr));

    Address wsGwAddr = cluster.gateway(WSGW).address();
    ServiceCall wsServiceCall =
        new ServiceCall()
            .transport(
                GatewayClientTransports.rsocketGatewayClientTransport(
                    GatewayClientSettings.builder().address(rsGwAddr).build()))
            .router(new StaticAddressRouter(rsGwAddr));
  }

  @Test
  void testHandler0() {}

  @Service
  public interface TestService {

    @ServiceMethod
    Mono<String> oneErr(String name);

    @ServiceMethod
    Flux<String> manyErr(String name);

    @ServiceMethod
    Mono<String> one(String name);

    @ServiceMethod
    Flux<String> many(String name);
  }

  private static class TestServiceImpl implements TestService {

    @Override
    public Mono<String> oneErr(String name) {
      return Mono.error(new ForbiddenException("error"));
    }

    @Override
    public Flux<String> manyErr(String name) {
      return Flux.error(new ForbiddenException("error"));
    }

    @Override
    public Mono<String> one(String name) {
      return Mono.just(name);
    }

    @Override
    public Flux<String> many(String name) {
      return Flux.just(name);
    }
  }

  private static class TestHandler implements SessionEventsHandler {

    CountDownLatch msgLatch = new CountDownLatch(1);
    CountDownLatch errLatch = new CountDownLatch(1);
    CountDownLatch connLatch = new CountDownLatch(1);
    CountDownLatch disconnLatch = new CountDownLatch(1);

    @Override
    public Object mapMessage(Object s, Object req) {
      msgLatch.countDown();
      return req;
    }

    @Override
    public void onError(Object s, Throwable throwable, Object req, Object resp) {
      errLatch.countDown();
    }

    @Override
    public void onSessionOpen(Object s) {
      connLatch.countDown();
    }

    @Override
    public void onSessionClose(Object s) {
      disconnLatch.countDown();
    }
  }
}
