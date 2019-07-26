package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.GatewayClientTransport;
import io.scalecube.services.gateway.transport.StaticAddressRouter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public abstract class AbstractLocalGatewayExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLocalGatewayExtension.class);
  private final Object serviceInstance;
  private final Function<GatewayOptions, Gateway> gatewaySupplier;
  private final Function<GatewayClientSettings, GatewayClient> gatewayClientSupplier;

  private List<GatewayClient> clients = new ArrayList<>();

  private String gatewayId;
  private Microservices gateway;
  private Address gatewayAddress;

  protected AbstractLocalGatewayExtension(
      Object serviceInstance,
      Function<GatewayOptions, Gateway> gatewaySupplier,
      Function<GatewayClientSettings, GatewayClient> gatewayClientSupplier) {
    this.serviceInstance = serviceInstance;
    this.gatewaySupplier = gatewaySupplier;
    this.gatewayClientSupplier = gatewayClientSupplier;
  }

  @Override
  public final void beforeAll(ExtensionContext context) {
    gateway =
        Microservices.builder()
            .services(serviceInstance)
            .gateway(
                options -> {
                  Gateway gateway = gatewaySupplier.apply(options);
                  gatewayId = gateway.id();
                  return gateway;
                })
            .startAwait();
  }

  @Override
  public final void beforeEach(ExtensionContext context) {
    gatewayAddress = gateway.gateway(gatewayId).address();
  }

  @Override
  public final void afterEach(ExtensionContext context) {
    if (clients != null) {
      Mono.whenDelayError(clients.stream().map(GatewayClient::close).toArray(Mono[]::new))
          .onErrorResume(th -> Mono.empty())
          .block(Duration.ofSeconds(10));
      clients.clear();
    }
  }

  @Override
  public final void afterAll(ExtensionContext context) {
    shutdownGateway();
  }

  /**
   * Returns a new service call by the given gateway client.
   *
   * @param gatewayClient gateway client
   * @return service call
   */
  public ServiceCall serviceCall(GatewayClient gatewayClient) {
    return new ServiceCall()
        .transport(new GatewayClientTransport(gatewayClient))
        .router(new StaticAddressRouter(gatewayAddress));
  }

  /**
   * Returns a new gateway client.
   *
   * @return gateway client
   */
  public GatewayClient gatewayClient() {
    GatewayClientSettings clintSettings =
        GatewayClientSettings.builder().address(gatewayAddress).build();
    GatewayClient gatewayClient = gatewayClientSupplier.apply(clintSettings);
    clients.add(gatewayClient);
    return gatewayClient;
  }

  private void shutdownGateway() {
    if (gateway != null) {
      try {
        gateway.shutdown().block();
      } catch (Throwable ignore) {
        // ignore
      }
      LOGGER.info("Shutdown gateway {}", gateway);
    }
  }
}
