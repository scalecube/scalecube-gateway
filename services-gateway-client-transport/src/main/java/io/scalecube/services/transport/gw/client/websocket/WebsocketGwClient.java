package io.scalecube.services.transport.gw.client.websocket;

import io.netty.buffer.ByteBuf;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.transport.gw.client.GatewayClient;
import io.scalecube.services.transport.gw.client.GwClientCodec;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.LoopResources;

public final class WebsocketGwClient implements GatewayClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketClientTransport.class);

  private static final String STREAM_ID = "sid";
  private static final String SIGNAL = "sig";

  private static final AtomicReferenceFieldUpdater<WebsocketGwClient, Mono> websocketMonoUpdater =
      AtomicReferenceFieldUpdater.newUpdater(WebsocketGwClient.class, Mono.class, "websocketMono");

  private final GwClientCodec<ByteBuf> codec;
  private final GwClientSettings settings;
  private final HttpClient httpClient;
  private final AtomicLong sidCounter = new AtomicLong();

  @SuppressWarnings("unused")
  private volatile Mono<?> websocketMono;

  /**
   * Creates instance of websocket client transport.
   *
   * @param settings client settings
   * @param codec client codec.
   */
  public WebsocketGwClient(GwClientSettings settings, GwClientCodec<ByteBuf> codec) {
    this.settings = settings;
    this.codec = codec;
    httpClient =
        HttpClient.newConnection()
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
          long sid = sidCounter.incrementAndGet();
          ByteBuf byteBuf = encodeRequest(request, sid);
          return getOrConnect()
              .flatMap(
                  session ->
                      session
                          .send(byteBuf, sid)
                          .then(
                              Mono.<ServiceMessage>create(
                                  sink ->
                                      session
                                          .receive(sid)
                                          .subscribe(sink::success, sink::error, sink::success)))
                          .doOnCancel(() -> handleCancel(sid, session)));
        });
  }

  @Override
  public Flux<ServiceMessage> requestStream(ServiceMessage request) {
    return Flux.defer(
        () -> {
          long sid = sidCounter.incrementAndGet();
          ByteBuf byteBuf = encodeRequest(request, sid);
          return getOrConnect()
              .flatMapMany(
                  session ->
                      session
                          .send(byteBuf, sid)
                          .thenMany(
                              Flux.<ServiceMessage>create(
                                  sink ->
                                      session
                                          .receive(sid)
                                          .subscribe(sink::next, sink::error, sink::complete)))
                          .doOnCancel(() -> handleCancel(sid, session)));
        });
  }

  @Override
  public Flux<ServiceMessage> requestChannel(Flux<ServiceMessage> requests) {
    return Flux.error(
        new UnsupportedOperationException(
            "Request channel is not supported by WebSocket transport implementation"));
  }

  @Override
  public Mono<Void> close() {
    return Mono.defer(
        () -> {
          // noinspection unchecked
          Mono<WebsocketSession> curr = websocketMonoUpdater.get(this);
          return (curr == null ? Mono.<Void>empty() : curr.flatMap(WebsocketSession::close))
              .doOnTerminate(() -> LOGGER.info("Closed websocket client sdk transport"));
        });
  }

  public GwClientCodec<ByteBuf> getCodec() {
    return codec;
  }

  private Mono<WebsocketSession> getOrConnect() {
    // noinspection unchecked
    return Mono.defer(() -> websocketMonoUpdater.updateAndGet(this, this::getOrConnect0));
  }

  private Mono<WebsocketSession> getOrConnect0(Mono<WebsocketSession> prev) {
    if (prev != null) {
      return prev;
    }

    return httpClient
        .websocket()
        .uri("/")
        .connect()
        .map(
            connection -> {
              WebsocketSession session = new WebsocketSession(codec, connection);
              LOGGER.info("Created {} on {}:{}", session, settings.host(), settings.port());
              // setup shutdown hook
              session
                  .onClose()
                  .doOnTerminate(
                      () -> {
                        websocketMonoUpdater.getAndSet(this, null); // clear reference
                        LOGGER.info(
                            "Closed {} on {}:{}", session, settings.host(), settings.port());
                      })
                  .subscribe(
                      null, th -> LOGGER.warn("Exception on closing session={}", session.id(), th));
              return session;
            })
        .doOnError(
            ex -> {
              LOGGER.warn(
                  "Failed to connect on {}:{}, cause: {}", settings.host(), settings.port(), ex);
              websocketMonoUpdater.getAndSet(this, null); // clear reference
            })
        .cache();
  }

  private Disposable handleCancel(long sid, WebsocketSession session) {
    ByteBuf byteBuf =
        codec.encode(
            ServiceMessage.builder()
                .header(STREAM_ID, sid)
                .header(SIGNAL, Signal.CANCEL.codeAsString())
                .build());
    return session
        .send(byteBuf, sid)
        .subscribe(
            null,
            th ->
                LOGGER.error(
                    "Exception on sending CANCEL signal for session={}", session.id(), th));
  }

  private ByteBuf encodeRequest(ServiceMessage message, long sid) {
    return codec.encode(ServiceMessage.from(message).header(STREAM_ID, sid).build());
  }
}
