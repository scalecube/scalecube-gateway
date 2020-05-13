package io.scalecube.services.testservice;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Principal;
import io.scalecube.services.exceptions.BadRequestException;
import io.scalecube.services.exceptions.ForbiddenException;
import java.util.Optional;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SecuredServiceImpl implements SecuredService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SecuredServiceImpl.class);

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
    Optional<String> authResult = authRegistry.addAuth(Long.parseLong(sessionId), req);
    if (authResult.isPresent()) {
      return Mono.just(req);
    } else {
      return Mono.error(
          new ForbiddenException("User not allowed to use this service: " + req));
    }
  }

  @Override
  public Mono<String> requestOne(String req) {
    return Mono.deferWithContext(context -> Mono.just(context.get(String.class)))
        .flatMap(auth -> {
          LOGGER.info("User {} has accessed secured call", auth);
          return Mono.just(auth + "@" + req);
        });
  }

  @Override
  public Flux<String> requestN(Integer times) {
    if (times <= 0) {
      return Flux.empty();
    }
    return Flux.fromStream(IntStream.range(0, times).mapToObj(String::valueOf));
  }
}
