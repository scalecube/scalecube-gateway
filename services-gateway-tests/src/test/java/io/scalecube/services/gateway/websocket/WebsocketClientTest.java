package io.scalecube.services.gateway.websocket;

import io.netty.buffer.ByteBuf;
import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.gateway.BaseTest;
import io.scalecube.services.gateway.TestGatewaySessionHandler;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.GatewayClientTransport;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.transport.StaticAddressRouter;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClient;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class WebsocketClientTest extends BaseTest {

  public static final GatewayClientCodec<ByteBuf> CLIENT_CODEC =
      GatewayClientTransports.WEBSOCKET_CLIENT_CODEC;

  private static Microservices gateway;
  private static Address gatewayAddress;
  private static Microservices service;
  private static GatewayClient client;

  @BeforeAll
  static void beforeAll() {
    gateway =
        Microservices.builder()
            .discovery("gateway", ScalecubeServiceDiscovery::new)
            .transport(RSocketServiceTransport::new)
            .gateway(
                options -> new WebsocketGateway(options.id("WS"), new TestGatewaySessionHandler()))
            .startAwait();
    gatewayAddress = gateway.gateway("WS").address();

    service =
        Microservices.builder()
            .discovery(
                "service",
                serviceEndpoint ->
                    new ScalecubeServiceDiscovery(serviceEndpoint)
                        .membership(
                            opts -> opts.seedMembers(gateway.discovery("gateway").address())))
            .transport(RSocketServiceTransport::new)
            .services(new TestServiceImpl())
            .startAwait();
  }

  @AfterEach
  void afterEach() {
    final GatewayClient client = WebsocketClientTest.client;
    if (client != null) {
      client.close();
    }
  }

  @AfterAll
  static void afterAll() {
    final GatewayClient client = WebsocketClientTest.client;
    if (client != null) {
      client.close();
    }
    Flux.concat(
            Mono.justOrEmpty(gateway).map(Microservices::shutdown),
            Mono.justOrEmpty(service).map(Microservices::shutdown))
        .then()
        .block();
  }

  @RepeatedTest(300)
  void testMessageSequence() {

    client =
        new WebsocketGatewayClient(
            GatewayClientSettings.builder().address(gatewayAddress).build(), CLIENT_CODEC);

    ServiceCall serviceCall =
        new ServiceCall()
            .transport(new GatewayClientTransport(client))
            .router(new StaticAddressRouter(gatewayAddress));

    int count = ThreadLocalRandom.current().nextInt(42) + 24;

    StepVerifier.create(serviceCall.api(TestService.class).many(count) /*.log("<<< ")*/)
        .expectNextSequence(IntStream.range(0, count).boxed().collect(Collectors.toList()))
        .expectComplete()
        .verify(Duration.ofSeconds(10));
  }

  @Service
  public interface TestService {

    @ServiceMethod("many")
    Flux<Integer> many(int count);
  }

  private static class TestServiceImpl implements TestService {

    @Override
    public Flux<Integer> many(int count) {
      return Flux.range(0, count);
    }
  }
}
