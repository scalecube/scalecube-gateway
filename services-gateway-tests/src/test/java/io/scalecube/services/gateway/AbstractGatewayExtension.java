package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.discovery.api.ServiceDiscovery;
import io.scalecube.services.routing.Router;
import io.scalecube.services.transport.gw.GwTransportBootstraps;
import io.scalecube.services.transport.gw.StaticAddressRouter;
import io.scalecube.services.transport.gw.client.GwClientSettings;
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
  private final Microservices gateway;
  private Microservices client;
  // TODO: [sergeyr] actually that could be useful in some circumstances. use a stub for now
  protected final GwClientSettings clientSettings = GwClientSettings.builder().build();

  private ServiceCall serviceCall;
  private Microservices services;
  private Router clientRouter;

  protected AbstractGatewayExtension(
      Object serviceInstance, Function<GatewayOptions, Gateway> gatewayFactory) {
    this.serviceInstance = serviceInstance;
    gateway =
        Microservices.builder()
            .discovery(ScalecubeServiceDiscovery::new)
            .transport(GwTransportBootstraps::rsocketServiceTransport)
            .gateway(gatewayFactory)
            .startAwait();
  }

  @Override
  public final void beforeAll(ExtensionContext context) {
    Address gatewayAddress = gateway.gateway(gatewayAliasName()).address();
    startServices();
    clientRouter = new StaticAddressRouter(gatewayAddress);
    client = Microservices.builder().transport(this::gwClientTransport).startAwait();
  }

  @Override
  public final void beforeEach(ExtensionContext context) {
    // if services was shutdown in test need to start them again
    if (services == null) {
      startServices();
    }
    serviceCall = client.call().router(clientRouter);
  }

  @Override
  public final void afterAll(ExtensionContext context) {
    shutdownServices();
    shutdownGateway();
  }

  @Override
  public final void afterEach(ExtensionContext context) {
    shutdownClient();
  }

  public ServiceCall client() {
    return serviceCall;
  }

  protected abstract ServiceTransportBootstrap gwClientTransport(ServiceTransportBootstrap op);

  protected abstract String gatewayAliasName();

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
