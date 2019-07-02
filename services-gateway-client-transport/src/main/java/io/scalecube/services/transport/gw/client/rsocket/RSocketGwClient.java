package io.scalecube.services.transport.gw.client.rsocket;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.ConnectionClosedException;
import io.scalecube.services.transport.gw.client.GatewayClient;
import io.scalecube.services.transport.gw.client.GwClientCodec;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public final class RSocketGwClient implements GatewayClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGwClient.class);

  private static final AtomicReferenceFieldUpdater<RSocketGwClient, Mono> rSocketMonoUpdater =
      AtomicReferenceFieldUpdater.newUpdater(RSocketGwClient.class, Mono.class, "rsocketMono");

  private final GwClientSettings settings;
  private final GwClientCodec<Payload> codec;

  @SuppressWarnings("unused")
  private volatile Mono<?> rsocketMono;

  /**
   * Constructor for client sdk rsocket transport.
   *
   * @param settings client settings.
   * @param codec client codec.
   */
  public RSocketGwClient(GwClientSettings settings, GwClientCodec<Payload> codec) {
    this.settings = settings;
    this.codec = codec;
  }

  @Override
  public Mono<ServiceMessage> requestResponse(ServiceMessage request) {
    return Mono.defer(
        () -> {
          Payload payload = toPayload(request);
          return getOrConnect()
              .flatMap(
                  rsocket ->
                      rsocket
                          .requestResponse(payload)
                          .onErrorMap(
                              ClosedChannelException.class,
                              e -> new ConnectionClosedException("Connection closed")))
              .map(this::toMessage);
        });
  }

  @Override
  public Flux<ServiceMessage> requestStream(ServiceMessage request) {
    return Flux.defer(
        () -> {
          Payload payload = toPayload(request);
          return getOrConnect()
              .flatMapMany(
                  rsocket ->
                      rsocket
                          .requestStream(payload)
                          .onErrorMap(
                              ClosedChannelException.class,
                              e -> new ConnectionClosedException("Connection closed")))
              .map(this::toMessage);
        });
  }

  @Override
  public Flux<ServiceMessage> requestChannel(Flux<ServiceMessage> requests) {
    return Flux.defer(
        () -> {
          Flux<Payload> reqPayloads = requests.map(this::toPayload);
          return getOrConnect()
              .flatMapMany(
                  rsocket ->
                      rsocket
                          .requestChannel(reqPayloads)
                          .onErrorMap(
                              ClosedChannelException.class,
                              e -> new ConnectionClosedException("Connection closed")))
              .map(this::toMessage);
        });
  }

  @Override
  public Mono<Void> close() {
    return Mono.defer(
        () -> {
          // noinspection unchecked
          Mono<RSocket> curr = rSocketMonoUpdater.get(this);
          return (curr == null ? Mono.<Void>empty() : curr.flatMap(this::dispose))
              .doOnTerminate(() -> LOGGER.info("Closed rsocket client sdk transport"));
        });
  }

  public GwClientCodec<Payload> getCodec() {
    return codec;
  }

  private Mono<? extends Void> dispose(RSocket rsocket) {
    rsocket.dispose();
    return rsocket.onClose();
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

    return RSocketFactory.connect()
        .metadataMimeType(settings.contentType())
        .transport(createRSocketTransport(settings))
        .start()
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
                  .subscribe(null, th -> LOGGER.warn("Exception on closing rsocket: {}", th));
            })
        .doOnError(
            ex -> {
              LOGGER.warn(
                  "Failed to connect on {}:{}, cause: {}", settings.host(), settings.port(), ex);
              rSocketMonoUpdater.getAndSet(this, null); // clear reference
            })
        .cache();
  }

  private WebsocketClientTransport createRSocketTransport(GwClientSettings settings) {
    String path = "/";

    HttpClient httpClient =
        HttpClient.newConnection()
            .followRedirect(settings.followRedirect())
            .tcpConfiguration(
                tcpClient -> {
                  if (settings.sslProvider() != null) {
                    tcpClient = tcpClient.secure(settings.sslProvider());
                  }
                  return tcpClient.host(settings.host()).port(settings.port());
                });

    return WebsocketClientTransport.create(httpClient, path);
  }

  private Payload toPayload(ServiceMessage message) {
    return codec.encode(message);
  }

  private ServiceMessage toMessage(Payload payload) {
    return codec.decode(payload);
  }
}
