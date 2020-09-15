package io.scalecube.services.gateway.exeptions;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import io.scalecube.services.exceptions.ServiceProviderErrorMapper;

public class GatewayServiceProviderErrorMapper implements ServiceProviderErrorMapper {
  public GatewayServiceProviderErrorMapper() {}

  public ServiceMessage toMessage(String qualifier, Throwable throwable) {
    if (throwable instanceof SomeException) {
      final int errorCode = ((SomeException) throwable).errorCode();
      final int errorType = SomeException.ERROR_TYPE;
      final String errorMessage = throwable.getMessage();
      ServiceMessage serviceMessage = ServiceMessage.error(qualifier, errorType, errorCode, errorMessage);
      return serviceMessage;
    }

    return DefaultErrorMapper.INSTANCE.toMessage(qualifier, throwable);
  }
}
