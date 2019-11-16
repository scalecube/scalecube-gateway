package io.scalecube.services.testservice;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Principal;
import io.scalecube.services.exceptions.BadRequestException;
import io.scalecube.services.exceptions.ForbiddenException;
import java.util.Optional;
import reactor.core.publisher.Mono;

public class SecuredServiceImpl implements SecuredService {

  private final AuthRegistry authRegistry;

  public SecuredServiceImpl(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public Mono<String> createSession(ServiceMessage request) {
    String sessionId = request.header(AuthRegistry.SESSION_ID);
    if (sessionId == null) {
      return Mono.error(new BadRequestException("session Id is not present in request") {
      });
    }
    String req = request.data();
    Optional<String> authResult = authRegistry.addAuth(sessionId, req);
    if (authResult.isPresent()) {
      return Mono.just(req);
    } else {
      return Mono.error(
          new ForbiddenException("User not allowed to use this service: " + req));
    }
  }

  @Override
  public Mono<String> securedCall(String req, @Principal String auth) {
    System.out.println("User " + auth + " accessed secured call");
    return Mono.just(auth + "@" + req);
  }
}
