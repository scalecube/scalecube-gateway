package io.scalecube.services.gateway.websocket;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.testservice.AuthRegistry;
import io.scalecube.services.testservice.AuthRequest;
import io.scalecube.services.testservice.SecuredAuthenticator;
import io.scalecube.services.testservice.SecuredService;
import io.scalecube.services.testservice.SecuredServiceImpl;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

public class WebsocketLocalGatewayAuthTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(3);

  private static final String ALLOWED_USER = "VASYA_PUPKIN";
  private static final Set<String> ALLOWED_USERS =
      new HashSet<>(Collections.singletonList(ALLOWED_USER));

  private static final AuthRegistry AUTH_REG = new AuthRegistry(ALLOWED_USERS);

  @RegisterExtension
  static WsLocalWithAuthExtension extension =
      new WsLocalWithAuthExtension(
          new SecuredServiceImpl(AUTH_REG), new SecuredAuthenticator(AUTH_REG), AUTH_REG);

  private SecuredService clientService;

  @BeforeEach
  void initService() {
    clientService = extension.client().api(SecuredService.class);
  }

  @Test
  void testCreateSessionSucc() {
    StepVerifier.create(
            extension.client().requestOne(createSessionReq(ALLOWED_USER), AuthRequest.class))
        .expectNextCount(1)
        .expectComplete()
        .verify();
  }

  private static ServiceMessage createSessionReq(String username) {
    return ServiceMessage.builder()
        .qualifier("/" + SecuredService.NS + "/createSession")
        .data(new AuthRequest(username))
        .build();
  }
}
