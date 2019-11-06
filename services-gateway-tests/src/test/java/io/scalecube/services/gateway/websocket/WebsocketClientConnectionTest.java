package io.scalecube.services.gateway.websocket;

import static io.scalecube.services.gateway.TestUtils.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.gateway.BaseTest;
import io.scalecube.services.gateway.TestUtils;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.GatewayClientTransport;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.transport.StaticAddressRouter;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClient;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class WebsocketClientConnectionTest extends BaseTest {

  public static final GatewayClientCodec<ByteBuf> CLIENT_CODEC =
      GatewayClientTransports.WEBSOCKET_CLIENT_CODEC;

  private Microservices gateway;
  private Address gatewayAddress;
  private Microservices service;

  private static final AtomicInteger onCloseCounter = new AtomicInteger();
  private GatewayClient client;

  @BeforeEach
  void beforEach() {
    gateway =
        Microservices.builder()
            .discovery(ScalecubeServiceDiscovery::new)
            .transport(RSocketServiceTransport::new)
            .gateway(options -> new WebsocketGateway(options.id("WS")))
            .startAwait();

    gatewayAddress = gateway.gateway("WS").address();

    service =
        Microservices.builder()
            .discovery(
                serviceEndpoint ->
                    new ScalecubeServiceDiscovery(serviceEndpoint)
                        .options(
                            config ->
                                config.membership(
                                    opts -> opts.seedMembers(gateway.discovery().address()))))
            .transport(RSocketServiceTransport::new)
            .services(new TestServiceImpl())
            .startAwait();

    onCloseCounter.set(0);
  }

  @AfterEach
  void afterEach() {
    Flux.concat(
            Mono.justOrEmpty(client).doOnNext(GatewayClient::close).flatMap(GatewayClient::onClose),
            Mono.justOrEmpty(gateway).map(Microservices::shutdown),
            Mono.justOrEmpty(service).map(Microservices::shutdown))
        .then()
        .block();
  }

  @Test
  void testCloseServiceStreamAfterLostConnection() {
    client =
        new WebsocketGatewayClient(
            GatewayClientSettings.builder().address(gatewayAddress).build(), CLIENT_CODEC);

    ServiceCall serviceCall =
        new ServiceCall()
            .transport(new GatewayClientTransport(client))
            .router(new StaticAddressRouter(gatewayAddress));

    StepVerifier.create(serviceCall.api(TestService.class).manyNever().log("<<< "))
        .thenAwait(Duration.ofSeconds(1))
        .then(() -> client.close())
        .then(() -> client.onClose().block())
        .expectError(IOException.class)
        .verify(Duration.ofSeconds(10));

    TestUtils.await(() -> onCloseCounter.get() == 1).block(TIMEOUT);
    assertEquals(1, onCloseCounter.get());
  }

  @Test
  public void testCallRepeatedlyByInvalidAddress() {
    Address invalidAddress = Address.create("localhost", 5050);

    client =
        new WebsocketGatewayClient(
            GatewayClientSettings.builder().address(invalidAddress).build(), CLIENT_CODEC);

    ServiceCall serviceCall =
        new ServiceCall()
            .transport(new GatewayClientTransport(client))
            .router(new StaticAddressRouter(invalidAddress));

    for (int i = 0; i < 100; i++) {
      StepVerifier.create(serviceCall.api(TestService.class).manyNever().log("<<< "))
          .thenAwait(Duration.ofSeconds(1))
          .expectError(IOException.class)
          .verify(Duration.ofSeconds(10));
    }
  }

  @Service
  public interface TestService {

    @ServiceMethod("manyNever")
    Flux<Long> manyNever();
  }

  private static class TestServiceImpl implements TestService {

    @Override
    public Flux<Long> manyNever() {
      return Flux.<Long>never().log(">>> ").doOnCancel(onCloseCounter::incrementAndGet);
    }
  }
}
