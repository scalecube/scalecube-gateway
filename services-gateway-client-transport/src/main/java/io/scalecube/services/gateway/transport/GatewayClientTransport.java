package io.scalecube.services.gateway.transport;

import io.scalecube.net.Address;
import io.scalecube.services.transport.api.ClientChannel;
import io.scalecube.services.transport.api.ClientTransport;
import java.util.function.Function;

/** Already knows which type of gateway client to create. */
public class GatewayClientTransport implements ClientTransport {

  private final GatewayClientSettings gatewayClientSettings;
  private final Function<GatewayClientSettings, GatewayClient> clientBuilder;

  GatewayClientTransport(
      GatewayClientSettings gatewayClientSettings,
      Function<GatewayClientSettings, GatewayClient> clientBuilder) {
    this.gatewayClientSettings = gatewayClientSettings;
    this.clientBuilder = clientBuilder;
  }

  @Override
  public ClientChannel create(Address address) {
    GatewayClientSettings actualSettings =
        GatewayClientSettings.from(gatewayClientSettings)
            .host(address.host())
            .port(address.port())
            .build();
    return new GatewayClientChannel(clientBuilder.apply(actualSettings));
  }
}
