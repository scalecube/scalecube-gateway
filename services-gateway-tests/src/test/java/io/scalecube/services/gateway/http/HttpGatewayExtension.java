package io.scalecube.services.gateway.http;

import io.scalecube.services.gateway.AbstractGatewayExtension;
import io.scalecube.services.gateway.transport.GatewayClientTransports;

class HttpGatewayExtension extends AbstractGatewayExtension {

  private static final String GATEWAY_ALIAS_NAME = "http";

  HttpGatewayExtension(Object serviceInstance) {
    super(
        serviceInstance,
        opts -> new HttpGateway(opts.id(GATEWAY_ALIAS_NAME)),
        GatewayClientTransports::httpGatewayClientTransport);
  }
}
