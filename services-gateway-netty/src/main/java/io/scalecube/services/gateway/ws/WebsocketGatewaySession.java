package io.scalecube.services.gateway.ws;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.scalecube.services.gateway.GatewaySession;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;
import org.jctools.maps.NonBlockingHashMapLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.NettyPipeline.SendOptions;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

public final class WebsocketGatewaySession implements GatewaySession {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketGatewaySession.class);

  private static final String DEFAULT_CONTENT_TYPE = "application/json";

  private static final AtomicLong SESSION_ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

  private final Map<Long, Disposable> subscriptions = new NonBlockingHashMapLong<>(1024);

  private final WebsocketInbound inbound;
  private final WebsocketOutbound outbound;
  private final GatewayMessageCodec codec;

  private final String sessionId;
  private final String contentType;

  /**
   * Create a new websocket session with given handshake, inbound and outbound channels.
   *
   * @param codec - msg codec
   * @param httpRequest - Init session HTTP request
   * @param inbound - Websocket inbound
   * @param outbound - Websocket outbound
   */
  public WebsocketGatewaySession(
      GatewayMessageCodec codec,
      HttpServerRequest httpRequest,
      WebsocketInbound inbound,
      WebsocketOutbound outbound) {
    this.codec = codec;
    this.sessionId = Long.toHexString(SESSION_ID_GENERATOR.incrementAndGet());

    String contentType = httpRequest.requestHeaders().get(HttpHeaderNames.CONTENT_TYPE);
    this.contentType = Optional.ofNullable(contentType).orElse(DEFAULT_CONTENT_TYPE);
    this.inbound =
        (WebsocketInbound) inbound.withConnection(c -> c.onDispose(this::clearSubscriptions));
    this.outbound = (WebsocketOutbound) outbound.options(SendOptions::flushOnEach);
  }

  @Override
  public String sessionId() {
    return sessionId;
  }

  public String contentType() {
    return contentType;
  }

  /**
   * Method for receiving request messages coming a form of websocket frames.
   *
   * @return flux websocket {@link ByteBuf}
   */
  public Flux<ByteBuf> receive() {
    return inbound.aggregateFrames().receive().retain();
  }

  /**
   * Method to send normal response.
   *
   * @param response response
   * @return mono void
   */
  public Mono<Void> send(GatewayMessage response) {
    return Mono.defer(
        () -> {
          // send with publisher (defer buffer cleanup to netty)
          return outbound
              .sendObject(Mono.just(response).map(codec::encode).map(TextWebSocketFrame::new))
              .then()
              .doOnSuccessOrError((avoid, th) -> logSend(response, th));
        });
  }

  private void logSend(GatewayMessage response, Throwable th) {
    if (th == null) {
      LOGGER.debug("<< SEND success: {}, session={}", response, sessionId);
    } else {
      LOGGER.warn("<< SEND failed: {}, session={}, cause: {}", response, sessionId, th);
    }
  }

  /**
   * Close the websocket session.
   *
   * @return mono void
   */
  public Mono<Void> close() {
    return outbound.sendClose().then();
  }

  /**
   * Closes websocket session with <i>normal</i> status.
   *
   * @param reason close reason
   * @return mono void
   */
  public Mono<Void> close(String reason) {
    return outbound.sendClose(1000, reason).then();
  }

  /**
   * Lambda setter for reacting on channel close occurrence.
   *
   * @param disposable function to run when disposable would take place
   */
  public Mono<Void> onClose(Disposable disposable) {
    return Mono.create(
        sink ->
            inbound.withConnection(
                connection ->
                    connection
                        .onDispose(disposable)
                        .onTerminate()
                        .subscribe(sink::success, sink::error, sink::success)));
  }

  /**
   * Disposing stored subscription by given stream id.
   *
   * @param streamId stream id
   * @return true of subscription was disposed
   */
  public boolean dispose(Long streamId) {
    boolean result = false;
    if (streamId != null) {
      Disposable disposable = subscriptions.remove(streamId);
      result = disposable != null;
      if (result) {
        LOGGER.debug("Dispose subscription by sid={}, session={}", streamId, sessionId);
        disposable.dispose();
      }
    }
    return result;
  }

  public boolean containsSid(Long streamId) {
    return streamId != null && subscriptions.containsKey(streamId);
  }

  /**
   * Saves (if not already saved) by stream id a subscription of service call coming in form of
   * {@link Disposable} reference.
   *
   * @param streamId stream id
   * @param disposable service subscription
   * @return true if disposable subscription was stored
   */
  public boolean register(Long streamId, Disposable disposable) {
    boolean result = false;
    if (!disposable.isDisposed()) {
      result = subscriptions.putIfAbsent(streamId, disposable) == null;
    }
    if (result) {
      LOGGER.debug("Registered subscription with sid={}, session={}", streamId, sessionId);
    }
    return result;
  }

  private void clearSubscriptions() {
    if (subscriptions.size() > 1) {
      LOGGER.debug("Clear all {} subscriptions on session={}", subscriptions.size(), sessionId);
    } else if (subscriptions.size() == 1) {
      LOGGER.debug("Clear 1 subscription on session={}", sessionId);
    }
    subscriptions.forEach((sid, disposable) -> disposable.dispose());
    subscriptions.clear();
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", WebsocketGatewaySession.class.getSimpleName() + "[", "]")
        .add(sessionId)
        .toString();
  }
}
