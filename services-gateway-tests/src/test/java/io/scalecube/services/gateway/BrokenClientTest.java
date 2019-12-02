package io.scalecube.services.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.IllegalReferenceCountException;
import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.ServiceInfo;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.GatewayClientTransports;
import io.scalecube.services.gateway.transport.StaticAddressRouter;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

public class BrokenClientTest {

  public static final String WSGW = "WSGW";

  public static void main(String[] args) throws InterruptedException {
    Microservices server = server();

    final TestServiceAuth api = client(server).api(TestServiceAuth.class);
    System.out.println("Sending requests continuously");
    for (int i = 0; ; i++) {
      TimeUnit.MILLISECONDS.sleep(100);

      boolean causeError = ThreadLocalRandom.current().nextInt(100) % 7 == 0;
      final Mono<String> stringMono = causeError
          ? api.serviceCallErr("ping" + i)
          : api.serviceCallSucc("ping" + i);
      stringMono.subscribe(n -> System.out.println("NEXT >>" + n),
          th -> System.err.println("FATAL ERROR >> " + th.getMessage()));
    }

  }

  private static Microservices server() {

    Object serviceInstance = ServiceInfo
        .fromServiceInstance(new TestServiceAuthImpl())
        .build();

    return Microservices.builder()
        .services(serviceInstance)
        .gateway(
            opts -> new WebsocketGateway(opts.id(WSGW)))
        .startAwait();
  }


  private static ServiceCall client(Microservices server) {
    Address address = server.gateway(WSGW).address();
    GatewayClientSettings settings = GatewayClientSettings.builder().address(address).build();
    return
        new ServiceCall()
            .transport(GatewayClientTransports.websocketGatewayClientTransport(settings))
            .router(new StaticAddressRouter(address));
  }

  @Test
  public void testIssue361() throws InterruptedException {
    final DisposableServer disposableServer = HttpServer.create()
        .handle((req, res) -> req.receive()
            .aggregate()
            .asString()
            .flatMap(s -> res.sendString(Mono.just(s))
                .then())).bindNow(Duration.ofSeconds(30));

    final HttpClient client = HttpClient.newConnection()
        .tcpConfiguration(
            tcpClient -> tcpClient
                .host(disposableServer.host())
                .port(disposableServer.port())
        );

    for (int i = 0; i < 1000; i++) {

      TimeUnit.MILLISECONDS.sleep(300);
      boolean causeError = ThreadLocalRandom.current().nextInt(100) % 7 == 0;
      final String expected = causeError ? "ERROR"  + i : "HELLO" + i;
      final Mono<ByteBuf> request = causeError
          ? Mono.error(new IllegalStateException("ERROR" + i))
          : Mono.just(Unpooled.buffer().writeBytes(("HELLO" + i).getBytes()));

      final String resp = client.post().uri("/")
          .send(request)
          .responseSingle(
              (httpResponse, bbMono) ->
                  bbMono
                      .map(ByteBuf::retain)
                      .map(content -> {
                        byte[] bytes = new byte[content.readableBytes()];
                        content.readBytes(bytes);
                        return new String(bytes);
                      })).onErrorResume(t -> Mono.just(t.getMessage()))
          .block(Duration.ofSeconds(30));
      Assertions.assertEquals(expected, resp);
      System.out.println("Passed test for: " + resp);
    }
  }

  @Service("testService")
  public interface TestServiceAuth {

    @ServiceMethod
    Mono<String> serviceCallErr(String req);

    @ServiceMethod
    Mono<String> serviceCallSucc(String req);
  }

  public static class TestServiceAuthImpl implements TestServiceAuth {

    @Override
    public Mono<String> serviceCallErr(String req) {
//      System.err.println(">> SERVICE_METHOD ERR");
      throw new IllegalReferenceCountException();
//      return Mono.just("echo" + "@" + req);
    }

    @Override
    public Mono<String> serviceCallSucc(String req) {
//      System.err.println(">> SERVICE_METHOD SUCC: " + req);
//      throw new IllegalReferenceCountException();
      return Mono.just("echo" + "@" + req);
    }
  }
}
