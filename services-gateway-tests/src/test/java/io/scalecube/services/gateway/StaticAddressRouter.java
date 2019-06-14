package io.scalecube.services.gateway;

import io.scalecube.net.Address;
import io.scalecube.services.ServiceEndpoint;
import io.scalecube.services.ServiceMethodDefinition;
import io.scalecube.services.ServiceReference;
import io.scalecube.services.ServiceRegistration;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.registry.api.ServiceRegistry;
import io.scalecube.services.routing.Router;
import java.util.Collections;
import java.util.Optional;

public class StaticAddressRouter implements Router {

  private final Address address;

  public StaticAddressRouter(Address address) {
    this.address = address;
  }

  @Override
  public Optional<ServiceReference> route(ServiceRegistry serviceRegistry,
      ServiceMessage request) {

    return Optional.of(new ServiceReference(new ServiceMethodDefinition("gw-router"),
        new ServiceRegistration(request.qualifier(), Collections
            .emptyMap(), Collections.emptyList()),
        ServiceEndpoint.builder().address(address).build()));
  }
}
