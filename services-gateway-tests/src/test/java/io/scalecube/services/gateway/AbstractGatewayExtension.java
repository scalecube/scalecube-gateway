package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.discovery.api.ServiceDiscovery;
import io.scalecube.services.transport.api.ClientTransport;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGatewayExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGatewayExtension.class);

  private final Object serviceInstance;
  private final Function<GatewayOptions, Gateway> gatewaySupplier;
  private final Function<GwClientSettings, ClientTransport> clientSupplier;

  private String gatewayId;
  private Microservices gateway;
  private Microservices services;
  private ServiceCall clientServiceCall;

  protected AbstractGatewayExtension(
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
            .discovery(ScalecubeServiceDiscovery::new)
            .transport(RSocketServiceTransport::new)
            .gateway(
                options -> {
                  Gateway gateway = gatewaySupplier.apply(options);
                  gatewayId = gateway.id();
                  return gateway;
                })
            .startAwait();
    startServices();
  }

  @Override
  public final void beforeEach(ExtensionContext context) {
    // if services was shutdown in test need to start them again
    if (services == null) {
      startServices();
    }
    Address gatewayAddress = gateway.gateway(gatewayId).address();
    GwClientSettings clintSettings = GwClientSettings.builder().address(gatewayAddress).build();
    clientServiceCall = new ServiceCall(clientSupplier.apply(clintSettings), gatewayAddress);
  }

  @Override
  public final void afterEach(ExtensionContext context) {
    // no-op
  }

  @Override
  public final void afterAll(ExtensionContext context) {
    shutdownServices();
    shutdownGateway();
  }

  public ServiceCall client() {
    return clientServiceCall;
  }

  public void shutdownServices() {
    if (services != null) {
      try {
        services.shutdown().block();
      } catch (Throwable ignore) {
        // ignore
      }
      LOGGER.info("Shutdown services {}", services);

      // if this method is called in particular test need to indicate that services are stopped to
      // start them again before another test
      services = null;
    }
  }

  private void startServices() {
    services =
        Microservices.builder()
            .discovery(this::serviceDiscovery)
            .transport(RSocketServiceTransport::new)
            .services(serviceInstance)
            .startAwait();
    LOGGER.info("Started services {} on {}", services, services.serviceAddress());
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

  private ServiceDiscovery serviceDiscovery(ServiceEndpoint serviceEndpoint) {
    return new ScalecubeServiceDiscovery(serviceEndpoint)
        .options(opts -> opts.seedMembers(gateway.discovery().address()));
  }
}
