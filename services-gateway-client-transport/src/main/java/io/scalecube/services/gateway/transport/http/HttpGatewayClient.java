package io.scalecube.services.gateway.transport.http;

import io.netty.buffer.ByteBuf;
import io.scalecube.services.api.Qualifier;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.api.ServiceMessage.Builder;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import java.util.function.BiFunction;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

public final class HttpGatewayClient implements GatewayClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpGatewayClient.class);

  private final GatewayClientCodec<ByteBuf> codec;
  private final HttpClient httpClient;
  private final LoopResources loopResources;
  private final MonoProcessor<Void> close = MonoProcessor.create();
  private final MonoProcessor<Void> onClose = MonoProcessor.create();

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

    // Setup cleanup
    close
        .then(doClose())
        .doFinally(s -> onClose.onComplete())
        .doOnTerminate(() -> LOGGER.info("Closed HttpGatewayClient resources"))
        .subscribe(null, ex -> LOGGER.warn("Exception occurred on HttpGatewayClient close: " + ex));
  }

  @Override
  public Mono<ServiceMessage> requestResponse(ServiceMessage request) {
    return Mono.defer(
        () -> {
          BiFunction<HttpClientRequest, NettyOutbound, Publisher<Void>> sender =
              (httpRequest, out) -> {
                LOGGER.debug("Sending request {}", request);
                // prepare request headers
                request.headers().forEach(httpRequest::header);
                // send with publisher (defer buffer cleanup to netty)
                return out.sendObject(Mono.just(codec.encode(request))).then();
              };
          return httpClient
              .post()
              .uri(request.qualifier())
              .send(sender)
              .responseSingle(
                  (httpResponse, bbMono) ->
                      bbMono
                          .map(ByteBuf::retain)
                          .map(content -> toMessage(httpResponse, content)));
        });
  }

  @Override
  public Flux<ServiceMessage> requestStream(ServiceMessage request) {
    return Flux.error(
        new UnsupportedOperationException("requestStream is not supported by HTTP/1.x"));
  }

  @Override
  public Flux<ServiceMessage> requestChannel(Flux<ServiceMessage> requests) {
    return Flux.error(
        new UnsupportedOperationException("requestChannel is not supported by HTTP/1.x"));
  }

  @Override
  public void close() {
    close.onComplete();
  }

  @Override
  public Mono<Void> onClose() {
    return onClose;
  }

  private Mono<Void> doClose() {
    return Mono.defer(loopResources::disposeLater);
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
