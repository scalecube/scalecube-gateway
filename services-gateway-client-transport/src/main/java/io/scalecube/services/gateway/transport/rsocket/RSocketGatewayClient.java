package io.scalecube.services.gateway.transport.rsocket;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.ConnectionClosedException;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.LoopResources;

public final class RSocketGatewayClient implements GatewayClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGatewayClient.class);

  private static final AtomicReferenceFieldUpdater<RSocketGatewayClient, Mono> rSocketMonoUpdater =
      AtomicReferenceFieldUpdater.newUpdater(RSocketGatewayClient.class, Mono.class, "rsocketMono");

  private final GatewayClientSettings settings;
  private final GatewayClientCodec<Payload> codec;
  private final LoopResources loopResources;
  private final MonoProcessor<Void> close = MonoProcessor.create();
  private final MonoProcessor<Void> onClose = MonoProcessor.create();

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
        .then(doClose())
        .doFinally(s -> onClose.onComplete())
        .doOnTerminate(() -> LOGGER.info("Closed RSocketGatewayClient resources"))
        .subscribe(
            null, ex -> LOGGER.warn("Exception occurred on RSocketGatewayClient close: " + ex));
  }

  @Override
  public Mono<ServiceMessage> requestResponse(ServiceMessage request) {
    return Mono.defer(
        () ->
            getOrConnect()
                .flatMap(
                    rsocket ->
                        rsocket
                            .requestResponse(toPayload(request))
                            .doOnSubscribe(s -> LOGGER.debug("Sending request {}", request))
                            .onErrorMap(
                                ClosedChannelException.class,
                                e -> new ConnectionClosedException("Connection closed")))
                .map(this::toMessage));
  }

  @Override
  public Flux<ServiceMessage> requestStream(ServiceMessage request) {
    return Flux.defer(
        () ->
            getOrConnect()
                .flatMapMany(
                    rsocket ->
                        rsocket
                            .requestStream(toPayload(request))
                            .doOnSubscribe(s -> LOGGER.debug("Sending request {}", request))
                            .onErrorMap(
                                ClosedChannelException.class,
                                e -> new ConnectionClosedException("Connection closed")))
                .map(this::toMessage));
  }

  @Override
  public Flux<ServiceMessage> requestChannel(Flux<ServiceMessage> requests) {
    return Flux.defer(
        () ->
            getOrConnect()
                .flatMapMany(
                    rsocket ->
                        rsocket
                            .requestChannel(
                                requests
                                    .doOnNext(r -> LOGGER.debug("Sending request {}", r))
                                    .map(this::toPayload))
                            .onErrorMap(
                                ClosedChannelException.class,
                                e -> new ConnectionClosedException("Connection closed")))
                .map(this::toMessage));
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

  public GatewayClientCodec<Payload> getCodec() {
    return codec;
  }

  private Mono<RSocket> getOrConnect() {
    // noinspection unchecked
    return Mono.defer(() -> rSocketMonoUpdater.updateAndGet(this, this::getOrConnect0));
  }

  private Mono<RSocket> getOrConnect0(Mono prev) {
    if (prev != null) {
      // noinspection unchecked
      return prev;
    }

    return RSocketConnector.create()
        .payloadDecoder(PayloadDecoder.DEFAULT)
        .metadataMimeType(settings.contentType())
        .connect(createRSocketTransport(settings))
        .doOnSuccess(
            rsocket -> {
              LOGGER.info("Connected successfully on {}:{}", settings.host(), settings.port());
              // setup shutdown hook
              rsocket
                  .onClose()
                  .doOnTerminate(
                      () -> {
                        rSocketMonoUpdater.getAndSet(this, null); // clear reference
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
              rSocketMonoUpdater.getAndSet(this, null); // clear reference
            })
        .cache();
  }

  private WebsocketClientTransport createRSocketTransport(GatewayClientSettings settings) {
    String path = "/";

    HttpClient httpClient =
        HttpClient.newConnection()
            .followRedirect(settings.followRedirect())
            .tcpConfiguration(
                tcpClient -> {
                  if (settings.sslProvider() != null) {
                    tcpClient = tcpClient.secure(settings.sslProvider());
                  }
                  return tcpClient.runOn(loopResources).host(settings.host()).port(settings.port());
                });

    return WebsocketClientTransport.create(httpClient, path);
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
