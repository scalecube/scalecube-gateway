package io.scalecube.services.gateway.exeptions;

import io.scalecube.services.api.ErrorData;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import io.scalecube.services.exceptions.ServiceClientErrorMapper;

public class GatewayServiceClientErrorMapper implements ServiceClientErrorMapper {

  @Override
  public Throwable toError(ServiceMessage message) {
    if (SomeException.ERROR_TYPE == message.errorType()) {
      ErrorData data = message.data();
      if (SomeException.ERROR_CODE == data.getErrorCode()) {
        return new SomeException();
      }
    }
    return DefaultErrorMapper.INSTANCE.toError(message);
  }
}
