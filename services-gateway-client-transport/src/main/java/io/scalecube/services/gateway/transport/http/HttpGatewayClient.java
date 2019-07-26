package io.scalecube.services.gateway.transport.http;

import io.netty.buffer.ByteBuf;
import io.scalecube.services.api.Qualifier;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.api.ServiceMessage.Builder;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

public final class HttpGatewayClient implements GatewayClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpGatewayClient.class);

  private final GatewayClientCodec<ByteBuf> codec;
  private final HttpClient httpClient;
  private final LoopResources loopResources;

  /**
   * Creates instance of http client transport.
   *
   * @param settings client settings
   */
  public HttpGatewayClient(GatewayClientSettings settings, GatewayClientCodec<ByteBuf> codec) {
    this.codec = codec;
    this.loopResources = LoopResources.create("http-gateway-client");

    httpClient =
        HttpClient.create(ConnectionProvider.elastic("http-gateway-client"))
            .followRedirect(settings.followRedirect())
            .tcpConfiguration(
                tcpClient -> {
                  if (settings.sslProvider() != null) {
                    tcpClient = tcpClient.secure(settings.sslProvider());
                  }
                  return tcpClient.runOn(loopResources).host(settings.host()).port(settings.port());
                });
  }

  @Override
  public Mono<ServiceMessage> requestResponse(ServiceMessage request) {
    return Mono.defer(
        () -> {
          ByteBuf byteBuf = codec.encode(request);
          return httpClient
              .post()
              .uri(request.qualifier())
              .send(
                  (httpRequest, out) -> {
                    LOGGER.debug("Sending request {}", request);
                    // prepare request headers
                    request.headers().forEach(httpRequest::header);
                    return out.sendObject(byteBuf).then();
                  })
              .responseSingle(
                  (httpResponse, bbMono) ->
                      bbMono.map(ByteBuf::retain).map(content -> toMessage(httpResponse, content)));
        });
  }

  @Override
  public Flux<ServiceMessage> requestStream(ServiceMessage request) {
    return Flux.error(
        new UnsupportedOperationException("Request stream is not supported by HTTP/1.x"));
  }

  @Override
  public Flux<ServiceMessage> requestChannel(Flux<ServiceMessage> requests) {
    return Flux.error(
        new UnsupportedOperationException("Request channel is not supported by HTTP/1.x"));
  }

  @Override
  public Mono<Void> close() {
    return Mono.defer(loopResources::disposeLater)
        .doOnTerminate(() -> LOGGER.info("Closed HttpGatewayClient resources"));
  }

  public GatewayClientCodec<ByteBuf> getCodec() {
    return codec;
  }

  private ServiceMessage toMessage(HttpClientResponse httpResponse, ByteBuf content) {
    int httpCode = httpResponse.status().code();
    String qualifier = isError(httpCode) ? Qualifier.asError(httpCode) : httpResponse.uri();

    Builder builder = ServiceMessage.builder().qualifier(qualifier).data(content);
    // prepare response headers
    httpResponse
        .responseHeaders()
        .entries()
        .forEach(entry -> builder.header(entry.getKey(), entry.getValue()));
    ServiceMessage message = builder.build();

    LOGGER.debug("Received response {}", message);
    return message;
  }

  private boolean isError(int httpCode) {
    return httpCode >= 400 && httpCode <= 599;
  }
}
