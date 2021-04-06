package io.scalecube.services.gateway.rsocket;

import static io.scalecube.services.gateway.TestUtils.TIMEOUT;
import static io.scalecube.services.gateway.exeptions.GatewayErrorMapperImpl.ERROR_MAPPER;

import io.scalecube.services.ServiceInfo;
import io.scalecube.services.gateway.BaseTest;
import io.scalecube.services.gateway.exeptions.ErrorService;
import io.scalecube.services.gateway.exeptions.ErrorServiceImpl;
import io.scalecube.services.gateway.exeptions.SomeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

class RSocketClientErrorMapperTest extends BaseTest {

  @RegisterExtension
  static RSocketGatewayExtension extension =
      new RSocketGatewayExtension(
          ServiceInfo.fromServiceInstance(new ErrorServiceImpl())
              .errorMapper(ERROR_MAPPER)
              .build());

  private ErrorService service;

  @BeforeEach
  void initService() {
    service = extension.client().errorMapper(ERROR_MAPPER).api(ErrorService.class);
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
