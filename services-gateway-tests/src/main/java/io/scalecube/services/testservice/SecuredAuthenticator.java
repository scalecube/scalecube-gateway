package io.scalecube.services.testservice;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Authenticator;
import io.scalecube.services.exceptions.BadRequestException;
import io.scalecube.services.exceptions.ForbiddenException;
import reactor.core.publisher.Mono;

public class SecuredAuthenticator implements Authenticator<String> {

  private final AuthRegistry authRegistry;

  public SecuredAuthenticator(AuthRegistry authRegistry) {
    this.authRegistry = authRegistry;
  }

  @Override
  public Mono<String> authenticate(ServiceMessage message) {
    String sessionId = message.header(AuthRegistry.SESSION_ID);
    return sessionId == null
        ? Mono.error(new BadRequestException("No session id provided in request"))
        : authRegistry
            .getAuth(sessionId)
            .map(Mono::just)
            .orElse(Mono.error(new ForbiddenException("Session is not authenticated")));
  }
}
