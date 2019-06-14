package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.discovery.api.ServiceDiscovery;
import io.scalecube.services.transport.gw.GwTransportBootstraps;
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

public abstract class AbstractGatewayExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGatewayExtension.class);

  private final Object serviceInstance;
  private final Function<GatewayOptions, Gateway> gatewaySupplier;
  private final BiFunction<GwClientSettings, ServiceTransportBootstrap, ServiceTransportBootstrap> clientSupplier;

  private Microservices gateway;
  private String gatewayId;
  private Microservices services;
  private Microservices client;
  private LoopResources clientLoopResources;
  private ServiceCall serviceCall;

  protected AbstractGatewayExtension(
      Object serviceInstance, Function<GatewayOptions, Gateway> gatewaySupplier,
      BiFunction<GwClientSettings, ServiceTransportBootstrap, ServiceTransportBootstrap> clientSupplier) {
    this.serviceInstance = serviceInstance;
    this.gatewaySupplier = gatewaySupplier;
    this.clientSupplier = clientSupplier;
  }

  @Override
  public final void beforeAll(ExtensionContext context) {
    gateway =
        Microservices.builder()
            .discovery(ScalecubeServiceDiscovery::new)
            .transport(GwTransportBootstraps::rsocketServiceTransport)
            .gateway(options -> {
              Gateway gateway = gatewaySupplier.apply(options);
              gatewayId = gateway.id();
              return gateway;
            })
            .startAwait();
    startServices();
    clientLoopResources = LoopResources.create("gw-client-worker");
  }

  @Override
  public final void beforeEach(ExtensionContext context) {
    // if services was shutdown in test need to start them again
    if (services == null) {
      startServices();
    }

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
    shutdownServices();
    shutdownGateway();
  }

  public ServiceCall client() {
    return serviceCall;
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
            .transport(GwTransportBootstraps::rsocketServiceTransport)
            .services(serviceInstance)
            .startAwait();
    LOGGER.info("Started services {} on {}", services, services.serviceAddress());
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
