package io.scalecube.services.transport.gw.client.http;

import io.netty.buffer.ByteBuf;
import io.scalecube.services.api.Qualifier;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.api.ServiceMessage.Builder;
import io.scalecube.services.transport.gw.client.GatewayClient;
import io.scalecube.services.transport.gw.client.GwClientCodec;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;

public final class HttpGwClient implements GatewayClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpGwClient.class);

  private final GwClientCodec<ByteBuf> codec;
  private final reactor.netty.http.client.HttpClient httpClient;
  private final ConnectionProvider connectionProvider;

  /**
   * Creates instance of http client transport.
   *
   * @param settings client settings
   */
  public HttpGwClient(GwClientSettings settings, GwClientCodec<ByteBuf> codec) {
    this.codec = codec;
    connectionProvider = ConnectionProvider.elastic("http-client-transport");

    httpClient =
        reactor.netty.http.client.HttpClient.create(connectionProvider)
            .followRedirect(settings.followRedirect())
            .tcpConfiguration(
                tcpClient -> {
                  if (settings.sslProvider() != null) {
                    tcpClient = tcpClient.secure(settings.sslProvider());
                  }
                  return tcpClient.host(settings.host()).port(settings.port());
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
    return connectionProvider
        .disposeLater()
        .doOnTerminate(() -> LOGGER.info("Closed http gw client transport"));
  }

  public GwClientCodec<ByteBuf> getCodec() {
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
