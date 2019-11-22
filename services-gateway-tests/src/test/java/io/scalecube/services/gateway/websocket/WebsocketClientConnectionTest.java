package io.scalecube.services.gateway.websocket;

import static io.scalecube.services.gateway.TestUtils.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.gateway.BaseTest;
import io.scalecube.services.gateway.TestGatewaySessionHandler;
import io.scalecube.services.gateway.TestService;
import io.scalecube.services.gateway.TestServiceImpl;
import io.scalecube.services.gateway.TestUtils;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.GatewayClientTransport;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.transport.StaticAddressRouter;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClient;
import io.scalecube.services.gateway.transport.websocket.WebsocketSession;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.test.StepVerifier;

class WebsocketClientConnectionTest extends BaseTest {

  public static final GatewayClientCodec<ByteBuf> CLIENT_CODEC =
      GatewayClientTransports.WEBSOCKET_CLIENT_CODEC;
  private static final AtomicInteger onCloseCounter = new AtomicInteger();
  private Microservices gateway;
  private Address gatewayAddress;
  private Microservices service;
  private GatewayClient client;
  private TestGatewaySessionHandler sessionEventHandler;

  @BeforeEach
  void beforEach() {
    this.sessionEventHandler = new TestGatewaySessionHandler();
    gateway =
        Microservices.builder()
            .discovery(ScalecubeServiceDiscovery::new)
            .transport(RSocketServiceTransport::new)
            .gateway(options -> new WebsocketGateway(options.id("WS"), sessionEventHandler))
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
            .services(new TestServiceImpl(onCloseCounter::incrementAndGet))
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

  @Test
  public void testHandlerEvents() throws InterruptedException {
    // Test Connect
    client =
        new WebsocketGatewayClient(
            GatewayClientSettings.builder().address(gatewayAddress).build(), CLIENT_CODEC);

    TestService service =
        new ServiceCall()
            .transport(new GatewayClientTransport(client))
            .router(new StaticAddressRouter(gatewayAddress))
            .api(TestService.class);

    service.one("one").block(TIMEOUT);
    sessionEventHandler.connLatch.await(3, TimeUnit.SECONDS);
    Assertions.assertEquals(0, sessionEventHandler.connLatch.getCount());

    sessionEventHandler.msgLatch.await(3, TimeUnit.SECONDS);
    Assertions.assertEquals(0, sessionEventHandler.msgLatch.getCount());

    client.close();
    sessionEventHandler.disconnLatch.await(3, TimeUnit.SECONDS);
    Assertions.assertEquals(0, sessionEventHandler.disconnLatch.getCount());
  }

  @Test
  void testKeepalive()
      throws InterruptedException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
    int expectedKeepalives = 3;
    Duration keepaliveInterval = Duration.ofSeconds(3);
    CountDownLatch keepaliveLatch = new CountDownLatch(expectedKeepalives);
    client =
        new WebsocketGatewayClient(
            GatewayClientSettings.builder()
                .address(gatewayAddress)
                .keepaliveIntervalMs(keepaliveInterval)
                .build(),
            CLIENT_CODEC);

    Method getorConn = WebsocketGatewayClient.class.getDeclaredMethod("getOrConnect");
    getorConn.setAccessible(true);
    WebsocketSession session = ((Mono<WebsocketSession>) getorConn.invoke(client))
        .block(TIMEOUT);
    Field connectionField = WebsocketSession.class.getDeclaredField("connection");
    connectionField.setAccessible(true);
    Connection connection = (Connection) connectionField.get(session);
    connection.inbound().receive().aggregate().subscribe(n -> keepaliveLatch.countDown());

    client.requestStream(ServiceMessage.builder().qualifier("/test/manyNever").build());

    keepaliveLatch
        .await(keepaliveInterval.toMillis() * (expectedKeepalives + 1), TimeUnit.MILLISECONDS);

//    assertEquals(0, keepaliveLatch.getCount());
  }
}
