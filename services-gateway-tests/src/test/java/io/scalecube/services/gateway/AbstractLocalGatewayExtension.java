package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.transport.api.ClientTransport;
import io.scalecube.services.transport.gw.StaticAddressRouter;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.resources.LoopResources;

public abstract class AbstractLocalGatewayExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLocalGatewayExtension.class);
  private final Object serviceInstance;
  private final Function<GatewayOptions, Gateway> gatewaySupplier;
  private final Function<GwClientSettings, ClientTransport> clientSupplier;

  private Microservices gateway;
  private LoopResources clientLoopResources;
  private ServiceCall clientServiceCall;
  private String gatewayId;

  protected AbstractLocalGatewayExtension(
      Object serviceInstance,
      Function<GatewayOptions, Gateway> gatewaySupplier,
      Function<GwClientSettings, ClientTransport> clientSupplier) {
    this.serviceInstance = serviceInstance;
    this.gatewaySupplier = gatewaySupplier;
    this.clientSupplier = clientSupplier;
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
    clientLoopResources = LoopResources.create("gw-client-worker");
  }

  @Override
  public final void beforeEach(ExtensionContext context) {
    Address gwAddress = this.gateway.gateway(gatewayId).address();
    GwClientSettings settings =
        GwClientSettings.builder().address(gwAddress).build();
    clientServiceCall = new ServiceCall().transport(clientSupplier.apply(settings))
        .router(new StaticAddressRouter(gwAddress));
  }

  @Override
  public final void afterAll(ExtensionContext context) {
    Optional.ofNullable(clientLoopResources).ifPresent(LoopResources::dispose);
    shutdownGateway();
  }

  public ServiceCall client() {
    return clientServiceCall;
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
