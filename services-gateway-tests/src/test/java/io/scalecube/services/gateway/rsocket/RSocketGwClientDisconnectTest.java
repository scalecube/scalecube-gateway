package io.scalecube.services.gateway.rsocket;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.hamcrest.core.StringStartsWith.startsWith;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.examples.GreetingService;
import io.scalecube.services.examples.GreetingServiceImpl;
import io.scalecube.services.exceptions.ConnectionClosedException;
import io.scalecube.services.transport.gw.GwClientTransports;
import io.scalecube.services.transport.gw.StaticAddressRouter;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RSocketGwClientDisconnectTest {

  private static final String GATEWAY_ALIAS_NAME = "rsws";
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);
  private static final String JOHN = "John";

  private Microservices gwWithServices;
  private ServiceCall clientServiceCall;

  @BeforeEach
  void startClient() {
    gwWithServices =
        Microservices.builder()
            .services(new GreetingServiceImpl())
            .gateway(opts -> new RSocketGateway(opts.id(GATEWAY_ALIAS_NAME)))
            .discovery(ScalecubeServiceDiscovery::new)
            .transport(RSocketServiceTransport::new)
            .startAwait();
    Address gwAddress = gwWithServices.gateway(GATEWAY_ALIAS_NAME).address();
    GwClientSettings settings = GwClientSettings.builder().build();
    clientServiceCall =
        new ServiceCall()
            .transport(GwClientTransports.rsocketGwClientTransport(settings))
            .router(new StaticAddressRouter(gwAddress));
  }

  @Test
  void testServerDisconnection() {
    Duration shutdownAt = Duration.ofSeconds(1);

    StepVerifier.create(
            clientServiceCall
                .api(GreetingService.class)
                .many(JOHN)
                .doOnSubscribe(
                    subscription ->
                        Mono.delay(shutdownAt)
                            .doOnSuccess(ignore -> gwWithServices.shutdown().subscribe())
                            .subscribe()))
        .thenConsumeWhile(
            response -> {
              assertThat(response, startsWith("Greeting ("));
              assertThat(response, endsWith(") to: " + JOHN));
              return true;
            })
        .expectError(ConnectionClosedException.class)
        .verify(shutdownAt.plus(SHUTDOWN_TIMEOUT));
  }
}
