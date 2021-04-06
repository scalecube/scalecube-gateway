package io.scalecube.services.gateway.transport.rsocket;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.rsocket.util.EmptyPayload;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

public final class RSocketGatewayClient implements GatewayClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGatewayClient.class);

  @SuppressWarnings("rawtypes")
  private static final AtomicReferenceFieldUpdater<RSocketGatewayClient, Mono> rsocketMonoUpdater =
      AtomicReferenceFieldUpdater.newUpdater(RSocketGatewayClient.class, Mono.class, "rsocketMono");

  private final GatewayClientSettings settings;
  private final GatewayClientCodec<Payload> codec;
  private final LoopResources loopResources;

  private final Sinks.One<Void> close = Sinks.one();
  private final Sinks.One<Void> onClose = Sinks.one();

  @SuppressWarnings("unused")
  private volatile Mono<?> rsocketMono;

  /**
   * Constructor for gateway over rsocket client transport.
   *
   * @param settings client settings.
   * @param codec client codec.
   */
  public RSocketGatewayClient(GatewayClientSettings settings, GatewayClientCodec<Payload> codec) {
    this.settings = settings;
    this.codec = codec;
    this.loopResources = LoopResources.create("rsocket-gateway-client");

    // Setup cleanup
    close
        .asMono()
        .then(doClose())
        .doFinally(s -> onClose.tryEmitEmpty())
        .doOnTerminate(() -> LOGGER.info("Closed RSocketGatewayClient resources"))
        .subscribe(
            null, ex -> LOGGER.warn("Exception occurred on RSocketGatewayClient close: " + ex));
  }

  @Override
  public Mono<ServiceMessage> requestResponse(ServiceMessage request) {
    return getOrConnect()
        .flatMap(
            rsocket ->
                rsocket
                    .requestResponse(toPayload(request))
                    .doOnSubscribe(s -> LOGGER.debug("Sending request {}", request)))
        .map(this::toMessage);
  }

  @Override
  public Flux<ServiceMessage> requestStream(ServiceMessage request) {
    return getOrConnect()
        .flatMapMany(
            rsocket ->
                rsocket
                    .requestStream(toPayload(request))
                    .doOnSubscribe(s -> LOGGER.debug("Sending request {}", request)))
        .map(this::toMessage);
  }

  @Override
  public Flux<ServiceMessage> requestChannel(Flux<ServiceMessage> requests) {
    return getOrConnect()
        .flatMapMany(
            rsocket ->
                rsocket.requestChannel(
                    requests
                        .doOnNext(r -> LOGGER.debug("Sending request {}", r))
                        .map(this::toPayload)))
        .map(this::toMessage);
  }

  @Override
  public void close() {
    close.tryEmitEmpty();
  }

  @Override
  public Mono<Void> onClose() {
    return onClose.asMono();
  }

  private Mono<Void> doClose() {
    return Mono.defer(loopResources::disposeLater);
  }

  private Mono<RSocket> getOrConnect() {
    // noinspection unchecked
    return Mono.defer(() -> rsocketMonoUpdater.updateAndGet(this, this::getOrConnect0));
  }

  @SuppressWarnings("rawtypes")
  private Mono<RSocket> getOrConnect0(Mono prev) {
    if (prev != null) {
      // noinspection unchecked
      return prev;
    }

    Payload setupPayload = EmptyPayload.INSTANCE;
    if (!settings.headers().isEmpty()) {
      setupPayload = codec.encode(ServiceMessage.builder().headers(settings.headers()).build());
    }

    return RSocketConnector.create()
        .payloadDecoder(PayloadDecoder.DEFAULT)
        .setupPayload(setupPayload)
        .metadataMimeType(settings.contentType())
        .connect(createClientTransport(settings))
        .doOnSuccess(
            rsocket -> {
              LOGGER.info("Connected successfully on {}:{}", settings.host(), settings.port());
              // setup shutdown hook
              rsocket
                  .onClose()
                  .doOnTerminate(
                      () -> {
                        rsocketMonoUpdater.getAndSet(this, null); // clear reference
                        LOGGER.info("Connection closed on {}:{}", settings.host(), settings.port());
                      })
                  .subscribe(
                      null, th -> LOGGER.warn("Exception on closing rsocket: {}", th.toString()));
            })
        .doOnError(
            ex -> {
              LOGGER.warn(
                  "Failed to connect on {}:{}, cause: {}",
                  settings.host(),
                  settings.port(),
                  ex.toString());
              rsocketMonoUpdater.getAndSet(this, null); // clear reference
            })
        .cache();
  }

  private WebsocketClientTransport createClientTransport(GatewayClientSettings settings) {
    HttpClient httpClient =
        HttpClient.create(ConnectionProvider.newConnection())
            .headers(headers -> settings.headers().forEach(headers::add))
            .followRedirect(settings.followRedirect())
            .wiretap(settings.wiretap())
            .runOn(loopResources)
            .host(settings.host())
            .port(settings.port());

    if (settings.sslProvider() != null) {
      httpClient = httpClient.secure(settings.sslProvider());
    }

    return WebsocketClientTransport.create(httpClient, "/");
  }

  private Payload toPayload(ServiceMessage message) {
    return codec.encode(message);
  }

  private ServiceMessage toMessage(Payload payload) {
    ServiceMessage message = codec.decode(payload);
    LOGGER.debug("Received response {}", message);
    return message;
  }
}
