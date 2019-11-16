package io.scalecube.services.testservice;

import static io.scalecube.services.testservice.SecuredService.NS;

import io.scalecube.services.annotations.RequestType;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Auth;
import io.scalecube.services.auth.Principal;
import io.scalecube.services.examples.EchoRequest;
import reactor.core.publisher.Mono;

/** Authentication service and the service body itself in one class. */
@Service(NS)
public interface SecuredService {
  String NS = "gw.auth";

  @ServiceMethod
  @RequestType(String.class)
  Mono<String> createSession(ServiceMessage request);

  @ServiceMethod
  @RequestType(String.class)
  @Auth
  Mono<String> securedCall(String req, @Principal String auth);
}
