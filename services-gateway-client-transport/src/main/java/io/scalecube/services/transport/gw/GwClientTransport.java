package io.scalecube.services.transport.gw;

import io.scalecube.net.Address;
import io.scalecube.services.transport.api.ClientChannel;
import io.scalecube.services.transport.api.ClientTransport;
import io.scalecube.services.transport.gw.client.GatewayClient;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import java.util.function.Function;

/** Already knows which type of Gw client to create. */
public class GwClientTransport implements ClientTransport {

  private final GwClientSettings gwClientSettings;
  private final Function<GwClientSettings, GatewayClient> clientBuilder;

  GwClientTransport(
      GwClientSettings gwClientSettings, Function<GwClientSettings, GatewayClient> clientBuilder) {
    this.gwClientSettings = gwClientSettings;
    this.clientBuilder = clientBuilder;
  }

  @Override
  public ClientChannel create(Address address) {
    GwClientSettings actualSettings =
        GwClientSettings.from(gwClientSettings).host(address.host()).port(address.port()).build();
    return new GwClientChannel(clientBuilder.apply(actualSettings));
  }
}
