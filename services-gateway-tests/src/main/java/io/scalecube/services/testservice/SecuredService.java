package io.scalecube.services.testservice;

import static io.scalecube.services.testservice.SecuredService.NS;

import io.scalecube.services.annotations.RequestType;
import io.scalecube.services.annotations.Service;
import io.scalecube.services.annotations.ServiceMethod;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Auth;
import reactor.core.publisher.Flux;
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
  Mono<String> requestOne(String req);

  @ServiceMethod
  @RequestType(Integer.class)
  @Auth
  Flux<String> requestN(Integer req);
}
