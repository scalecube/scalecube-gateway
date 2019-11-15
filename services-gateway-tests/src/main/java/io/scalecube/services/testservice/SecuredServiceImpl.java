package io.scalecube.services.testservice;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Principal;
import io.scalecube.services.examples.EchoRequest;
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
  public Mono<AuthRequest> createSession(ServiceMessage request) {
    String sessionId = request.header(AuthRegistry.SESSION_ID);
    if (sessionId == null) {
      return Mono.error(new BadRequestException("session Id is not present in request") {});
    }
    AuthRequest req = request.data();
    Optional<String> authResult = authRegistry.addAuth(sessionId, req.getUsername());
    if (authResult.isPresent()) {
      return Mono.just(req);
    } else {
      return Mono.error(
          new ForbiddenException("User not allowed to use this service: " + req.getUsername()));
    }
  }

  @Override
  public Mono<EchoRequest> securedCall(EchoRequest req, @Principal String auth) {
    System.out.println("User " + auth + " accessed secured call");
    EchoRequest resp = new EchoRequest();
    resp.setName(auth + "@" + req.getName());
    return Mono.just(resp);
  }
}
