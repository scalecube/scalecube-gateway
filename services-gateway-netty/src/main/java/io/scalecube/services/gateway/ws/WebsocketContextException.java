package io.scalecube.services.gateway.ws;

import io.scalecube.services.gateway.ReferenceCountUtil;
import java.util.Optional;

public class WebsocketContextException extends RuntimeException {

  private final GatewayMessage request;
  private final GatewayMessage response;

  private WebsocketContextException(
      Throwable cause, GatewayMessage request, GatewayMessage response) {
    super(cause);
    this.request = request;
    this.response = response;
  }

  public static WebsocketContextException badRequest(String errorMessage, GatewayMessage request) {
    return new WebsocketContextException(
        new io.scalecube.services.exceptions.BadRequestException(errorMessage), request, null);
  }

  public static WebsocketContextException wrap(
      Throwable th, GatewayMessage request, GatewayMessage response) {
    return new WebsocketContextException(th, request, response);
  }

  public GatewayMessage request() {
    return request;
  }

  public GatewayMessage response() {
    return response;
  }

  public WebsocketContextException releaseRequest() {
    Optional.ofNullable(request)
        .map(GatewayMessage::data)
        .ifPresent(ReferenceCountUtil::safestRelease);
    return this;
  }
}
