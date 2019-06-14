package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.Microservices.ServiceTransportBootstrap;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.routing.Router;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import java.util.function.Function;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLocalGatewayExtension
    implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLocalGatewayExtension.class);

  private final Microservices gateway;

  private ServiceCall serviceCall;
  private Router clientRouter;
  private Microservices client;
  // TODO: [sergeyr] actually that could be useful in some circumstances. use a stub for now
  protected final GwClientSettings clientSettings = GwClientSettings.builder().build();

  protected AbstractLocalGatewayExtension(
      Object serviceInstance, Function<GatewayOptions, Gateway> gatewayFactory) {
    gateway =
        Microservices.builder().services(serviceInstance).gateway(gatewayFactory).startAwait();
  }

  @Override
  public final void beforeAll(ExtensionContext context) {
    Address gatewayAddress = gateway.gateway(gatewayAliasName()).address();
    clientRouter = new StaticAddressRouter(gatewayAddress);
    client = Microservices.builder().transport(this::gwClientTransport).startAwait();
  }

  @Override
  public final void beforeEach(ExtensionContext context) {
    serviceCall = client.call().router(clientRouter);
  }

  @Override
  public final void afterAll(ExtensionContext context) {
    if (gateway != null) {
      try {
        gateway.shutdown().block();
      } catch (Throwable ignore) {
        // ignore
      }
      LOGGER.info("Shutdown gateway {}", gateway);
    }
  }

  @Override
  public final void afterEach(ExtensionContext context) {
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

  public ServiceCall client() {
    return serviceCall;
  }

  protected abstract ServiceTransportBootstrap gwClientTransport(ServiceTransportBootstrap op);

  protected abstract String gatewayAliasName();
}
