package io.scalecube.services.testservice;

import io.scalecube.services.annotations.RequestType;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Auth;
import io.scalecube.services.auth.Principal;
import io.scalecube.services.examples.EchoRequest;
import reactor.core.publisher.Mono;

/** Authentication service and the service body itself in one class. */
@Service
public interface SecuredService {
  String NS = "gw.auth";

  @ServiceMethod
  @RequestType(AuthRequest.class)
  Mono<AuthRequest> createSession(ServiceMessage request);

  @ServiceMethod
  @RequestType(AuthRequest.class)
  @Auth
  Mono<EchoRequest> securedCall(EchoRequest req, @Principal String auth);
}
