package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.resources.LoopResources;

public abstract class AbstractLocalGatewayExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLocalGatewayExtension.class);
  private final Object serviceInstance;
  private final Function<GatewayOptions, Gateway> gatewaySupplier;
  private final BiFunction<GwClientSettings, ServiceTransportBootstrap, ServiceTransportBootstrap> clientSupplier;

  private Microservices gateway;
  private Microservices client;
  private LoopResources clientLoopResources;
  private ServiceCall serviceCall;
  private String gatewayId;

  protected AbstractLocalGatewayExtension(
      Object serviceInstance, Function<GatewayOptions, Gateway> gatewaySupplier,
      BiFunction<GwClientSettings, ServiceTransportBootstrap, ServiceTransportBootstrap> clientSupplier) {
    this.serviceInstance = serviceInstance;
    this.gatewaySupplier = gatewaySupplier;
    this.clientSupplier = clientSupplier;
  }

  @Override
  public final void beforeAll(ExtensionContext context) {

    gateway =
        Microservices.builder().services(serviceInstance)
            .gateway(options -> {
              Gateway gateway = gatewaySupplier.apply(options);
              gatewayId = gateway.id();
              return gateway;
            })
            .startAwait();
    clientLoopResources = LoopResources.create("gw-client-worker");
  }

  @Override
  public final void beforeEach(ExtensionContext context) {
    Address address = this.gateway.gateway(gatewayId).address();
    client = Microservices.builder()
        .transport(op -> clientSupplier.apply(
            GwClientSettings.builder().address(address).loopResources(clientLoopResources).build(),
            op))
        .startAwait();

    serviceCall = client.call().router(new StaticAddressRouter(address));
  }

  @Override
  public final void afterEach(ExtensionContext context) {
    shutdownClient();
  }

  @Override
  public final void afterAll(ExtensionContext context) {
    Optional.ofNullable(clientLoopResources).ifPresent(LoopResources::dispose);
    shutdownGateway();
  }

  public ServiceCall client() {
    return serviceCall;
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

  private void shutdownClient() {
    if (client != null) {
      try {
        client.shutdown().block();
      } catch (Throwable ignore) {
        // ignore
      }
      LOGGER.info("Shutdown services {}", client);

      // if this method is called in particular test need to indicate that services are stopped to
      // start them again before another test
      client = null;
      serviceCall = null;
    }
  }
}
