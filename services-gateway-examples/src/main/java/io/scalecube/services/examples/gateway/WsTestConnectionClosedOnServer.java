package io.scalecube.services.examples.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.Gateway;
import io.scalecube.services.gateway.GatewayOptions;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.ws.GatewayMessage;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.gateway.ws.WebsocketGatewayHandler;
import io.scalecube.services.gateway.ws.WebsocketSession;
import io.scalecube.services.transport.api.ClientTransport;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

public class WsTestConnectionClosedOnServer {

  public static void main(String[] args) throws Exception {
    CountDownLatch[] latches = new CountDownLatch[2];
    latches[0] = new CountDownLatch(1);
    latches[1] = new CountDownLatch(1);

    AtomicInteger connections = new AtomicInteger();

    WebsocketGateway wsGateway =
        new WebsocketGateway(
            new GatewayOptions().call(new ServiceCall()).port(7070),
            new WebsocketGatewayHandler() {

              @Override
              public void onConnection(Connection connection) {
                Mono.delay(Duration.ofSeconds(5))
                    .doOnSuccess(
                        along -> {
                          System.out.println(">>> Disposing ws connection -- " + connection);
                          connection.dispose();
                          latches[connections.getAndIncrement()].countDown();
                        })
                    .subscribe();
              }

              @Override
              public void onError(
                  WebsocketSession session,
                  Throwable throwable,
                  GatewayMessage req,
                  GatewayMessage resp) {
                System.err.println("### WebsocketGatewayHandler.onError: " + throwable);
              }
            });
    Gateway gateway = wsGateway.start().block();
    Address address = gateway.address();
    System.err.println("### Gateway.address: " + address);

    ClientTransport clientTransport =
        GatewayClientTransports.websocketGatewayClientTransport(
            GatewayClientSettings.builder().address(address).build());

    System.out.println("### Calling 1");
    Mono<ServiceMessage> mono =
        clientTransport
            .create(address)
            .requestResponse(ServiceMessage.builder().qualifier("q").build(), null)
            .doOnError(System.err::println);

    try {
      mono.block();
    } catch (Exception ex) {
      System.err.println("### That's ok 1 -- " + ex);
    }

    System.err.println("### Awaiting 1 ...");
    latches[0].await();
    System.err.println("### Awaiting 1 -- OK");

    Thread.sleep(3000);

    System.out.println("### Calling 2");
    Mono<ServiceMessage> mono2 =
        clientTransport
            .create(address)
            .requestResponse(ServiceMessage.builder().qualifier("q").build(), null)
            .doOnError(System.err::println);

    try {
      mono2.block();
    } catch (Exception ex) {
      System.err.println("### That's ok 2 -- " + ex);
    }

    System.err.println("### Awaiting 2 ...");
    latches[1].await();
  }
}
