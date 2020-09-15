package io.scalecube.services.gateway.websocket;

import static io.scalecube.services.gateway.TestUtils.TIMEOUT;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.gateway.BaseTest;
import io.scalecube.services.gateway.exeptions.ErrorService;
import io.scalecube.services.gateway.exeptions.ErrorServiceImpl;
import io.scalecube.services.gateway.exeptions.GatewayServiceClientErrorMapper;
import io.scalecube.services.gateway.exeptions.GatewayServiceProviderErrorMapper;
import io.scalecube.services.gateway.exeptions.SomeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

class WebsocketLocalGatewayErrorMapperTest extends BaseTest {

  @RegisterExtension
  static WebsocketLocalGatewayExtension extension =
      new WebsocketLocalGatewayExtension(
          ServiceInfo.fromServiceInstance(new ErrorServiceImpl())
              .errorMapper(new GatewayServiceProviderErrorMapper())
              .build());

  private ErrorService service;

  @BeforeEach
  void initService() {
    service =
        extension
            .client()
            .errorMapper(new GatewayServiceClientErrorMapper())
            .api(ErrorService.class);
  }

  @Test
  void shouldReturnSomeExceptionOnFlux() {
    StepVerifier.create(service.manyError()).expectError(SomeException.class).verify(TIMEOUT);
  }

  @Test
  void shouldReturnSomeExceptionOnMono() {
    StepVerifier.create(service.oneError()).expectError(SomeException.class).verify(TIMEOUT);
  }
}
