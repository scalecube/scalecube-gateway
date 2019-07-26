package io.scalecube.services.gateway.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.GatewayClientTransport;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.transport.StaticAddressRouter;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClient;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class WebsocketClientConnectionTest {

  private Microservices gateway;
  private Address gatewayAddress;
  private Microservices service;

  private static final AtomicInteger onCloseCounter = new AtomicInteger();
  private WebsocketGatewayClient client;

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
            Mono.justOrEmpty(client)
                .doOnNext(WebsocketGatewayClient::close)
                .flatMap(WebsocketGatewayClient::onClose),
            Mono.justOrEmpty(gateway).map(Microservices::shutdown),
            Mono.justOrEmpty(service).map(Microservices::shutdown))
        .then()
        .block();
  }

  @Test
  void testCloseServiceStreamAfterLostConnection() {
    client =
        new WebsocketGatewayClient(
            GatewayClientSettings.builder().address(gatewayAddress).build(),
            GatewayClientTransports.WEBSOCKET_CLIENT_CODEC);

    ServiceCall serviceCall =
        new ServiceCall()
            .transport(new GatewayClientTransport(client))
            .router(new StaticAddressRouter(gatewayAddress));

    serviceCall.api(TestService.class).manyNever().subscribe();

    Mono.delay(Duration.ofSeconds(1)).block();

    client.close();
    client.onClose().block();

    Mono.delay(Duration.ofSeconds(1)).block();

    assertEquals(1, onCloseCounter.get());
  }

  @Service
  public interface TestService {

    @ServiceMethod("manyNever")
    Flux<Integer> manyNever();
  }

  private class TestServiceImpl implements TestService {

    @Override
    public Flux<Integer> manyNever() {
      return Flux.<Integer>never().doOnCancel(onCloseCounter::incrementAndGet);
    }
  }
}
