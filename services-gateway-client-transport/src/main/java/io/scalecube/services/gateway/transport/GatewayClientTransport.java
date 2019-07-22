package io.scalecube.services.gateway.transport;

import io.scalecube.net.Address;
import io.scalecube.services.transport.api.ClientChannel;
import io.scalecube.services.transport.api.ClientTransport;

/** Already knows which type of gateway client to create. */
public class GatewayClientTransport implements ClientTransport {

  private final GatewayClient gatewayClient;

  GatewayClientTransport(GatewayClient gatewayClient) {
    this.gatewayClient = gatewayClient;
  }

  @Override
  public ClientChannel create(Address address) {
    return new GatewayClientChannel(gatewayClient);
  }
}
