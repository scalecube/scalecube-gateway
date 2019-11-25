package io.scalecube.services.gateway.transport.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.transport.GatewayClient;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.netty.NettyPipeline.SendOptions;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.LoopResources;

public final class WebsocketGatewayClient implements GatewayClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketGatewayClient.class);

  private static final String STREAM_ID = "sid";
  private static final String SIGNAL = "sig";

  private static final AtomicReferenceFieldUpdater<WebsocketGatewayClient, Mono>
      websocketMonoUpdater =
      AtomicReferenceFieldUpdater.newUpdater(
          WebsocketGatewayClient.class, Mono.class, "websocketMono");

  private final GatewayClientCodec<ByteBuf> codec;
  private final GatewayClientSettings settings;
  private final HttpClient httpClient;
  private final AtomicLong sidCounter = new AtomicLong();
  private final LoopResources loopResources;
  private final MonoProcessor<Void> close = MonoProcessor.create();
  private final MonoProcessor<Void> onClose = MonoProcessor.create();

  @SuppressWarnings("unused")
  private volatile Mono<?> websocketMono;

  /**
   * Creates instance of websocket client transport.
   *
   * @param settings client settings
   * @param codec client codec.
   */
  public WebsocketGatewayClient(GatewayClientSettings settings, GatewayClientCodec<ByteBuf> codec) {
    this.settings = settings;
    this.codec = codec;
    this.loopResources = LoopResources.create("websocket-gateway-client");

    httpClient =
        HttpClient.newConnection()
            .followRedirect(settings.followRedirect())
            .tcpConfiguration(
                tcpClient -> {
                  if (settings.sslProvider() != null) {
                    tcpClient = tcpClient.secure(settings.sslProvider());
                  }
                  return tcpClient
                      .wiretap(true)
                      .runOn(loopResources)
                      .host(settings.host())
                      .port(settings.port());
                });

    // Setup cleanup
    close
        .then(doClose())
        .doFinally(s -> onClose.onComplete())
        .doOnTerminate(() -> LOGGER.info("Closed WebsocketGatewayClient resources"))
        .subscribe(
            null, ex -> LOGGER.warn("Exception occurred on WebsocketGatewayClient close: " + ex));
  }

  @Override
  public Mono<ServiceMessage> requestResponse(ServiceMessage request) {
    return Mono.defer(
        () -> {
          long sid = sidCounter.incrementAndGet();
          return getOrConnect()
              .flatMap(
                  session ->
                      session
                          .send(encodeRequest(request, sid), sid)
                          .doOnSubscribe(s -> LOGGER.debug("Sending request {}", request))
                          .then(session.newMonoProcessor(sid))
                          .doOnCancel(() -> handleCancel(sid, session))
                          .doFinally(s -> session.removeProcessor(sid)));
        });
  }

  @Override
  public Flux<ServiceMessage> requestStream(ServiceMessage request) {
    return Flux.defer(
        () -> {
          long sid = sidCounter.incrementAndGet();
          return getOrConnect()
              .flatMapMany(
                  session ->
                      session
                          .send(encodeRequest(request, sid), sid)
                          .doOnSubscribe(s -> LOGGER.debug("Sending request {}", request))
                          .thenMany(session.newUnicastProcessor(sid))
                          .doOnCancel(() -> handleCancel(sid, session))
                          .doFinally(s -> session.removeProcessor(sid)));
        });
  }

  @Override
  public Flux<ServiceMessage> requestChannel(Flux<ServiceMessage> requests) {
    return Flux.error(
        new UnsupportedOperationException(
            "requestChannel is not supported by WebSocket transport implementation"));
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
            c ->
                c.onReadIdle(
                    settings.keepaliveInterval().toMillis(),
                    () -> {
                      LOGGER.debug("Keepalive on readIdle");
                      c.outbound()
                          .options(SendOptions::flushOnEach)
                          .sendObject(new PingWebSocketFrame())
                          .then()
                          .subscribe(avoid -> System.out.println("SENT PING"));
                    })
                    .onWriteIdle(
                        settings.keepaliveInterval().toMillis(),
                        () -> {
                          LOGGER.debug("Keepalive on writeIdle");
                          c.outbound()
                              .options(SendOptions::flushOnEach)
                              .sendObject(new PingWebSocketFrame())
                              .then()
                              .subscribe(avoid -> System.out.println("SENT PING"));
                        }))
        .map(
            conn -> {
              WebsocketSession session = new WebsocketSession(codec, conn);
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
                      null,
                      th ->
                          LOGGER.warn(
                              "Exception on closing session={}, cause: {}",
                              session.id(),
                              th.toString()));
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

  private void handleCancel(long sid, WebsocketSession session) {
    ByteBuf byteBuf =
        codec.encode(
            ServiceMessage.builder()
                .header(STREAM_ID, sid)
                .header(SIGNAL, Signal.CANCEL.codeAsString())
                .build());
    session
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
