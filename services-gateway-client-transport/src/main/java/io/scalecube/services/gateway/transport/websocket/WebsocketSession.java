package io.scalecube.services.gateway.transport.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.scalecube.services.api.ErrorData;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.ConnectionClosedException;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.transport.api.ReferenceCountUtil;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;
import org.jctools.maps.NonBlockingHashMapLong;
import org.reactivestreams.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.publisher.UnicastProcessor;
import reactor.netty.Connection;
import reactor.netty.NettyPipeline.SendOptions;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

final class WebsocketSession {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketSession.class);

  private static final String STREAM_ID = "sid";
  private static final String SIGNAL = "sig";

  private final String id; // keep id for tracing
  private final GatewayClientCodec<ByteBuf> codec;
  private final Connection connection;
  private final WebsocketOutbound outbound;

  // processor by sid mapping
  private final Map<Long, Processor<ServiceMessage, ServiceMessage>> inboundProcessors =
      new NonBlockingHashMapLong<>(1024);

  WebsocketSession(GatewayClientCodec<ByteBuf> codec, Connection connection) {
    this.id = Integer.toHexString(System.identityHashCode(this));
    this.codec = codec;
    this.connection = connection
        .onReadIdle(3000, () -> connection.outbound().sendObject(new PingWebSocketFrame()));
    this.outbound = (WebsocketOutbound) connection.outbound().options(SendOptions::flushOnEach);

    WebsocketInbound inbound = (WebsocketInbound) connection.inbound();
    inbound
        .aggregateFrames()
        .receive()
        .retain()
        .subscribe(
            byteBuf -> {
              // decode message
              ServiceMessage message;
              try {
                message = codec.decode(byteBuf);
              } catch (Exception ex) {
                LOGGER.error("Response decoder failed: " + ex);
                return;
              }
              // ignore messages w/o sid
              if (!message.headers().containsKey(STREAM_ID)) {
                LOGGER.error("Ignore response: {} with null sid, session={}", message, id);
                Optional.ofNullable(message.data()).ifPresent(ReferenceCountUtil::safestRelease);
                return;
              }
              // processor?
              long sid = Long.parseLong(message.header(STREAM_ID));
              Processor<ServiceMessage, ServiceMessage> processor = inboundProcessors.get(sid);
              if (processor == null) {
                Optional.ofNullable(message.data()).ifPresent(ReferenceCountUtil::safestRelease);
                return;
              }
              // handle response message
              handleResponse(message, processor::onNext, processor::onError, processor::onComplete);
            });

    connection.onDispose(
        () ->
            inboundProcessors.forEach(
                (k, resp) -> resp.onError(new ConnectionClosedException("Connection closed"))));
  }

  public String id() {
    return id;
  }

  public MonoProcessor<ServiceMessage> newMonoProcessor(long sid) {
    return (MonoProcessor<ServiceMessage>)
        inboundProcessors.computeIfAbsent(
            sid,
            key -> {
              LOGGER.debug("Put sid={}, session={}", sid, id);
              return MonoProcessor.create();
            });
  }

  public UnicastProcessor<ServiceMessage> newUnicastProcessor(long sid) {
    return (UnicastProcessor<ServiceMessage>)
        inboundProcessors.computeIfAbsent(
            sid,
            key -> {
              LOGGER.debug("Put sid={}, session={}", sid, id);
              return UnicastProcessor.create();
            });
  }

  public void removeProcessor(long sid) {
    if (inboundProcessors.remove(sid) != null) {
      LOGGER.debug("Removed sid={}, session={}", sid, id);
    }
  }

  public Mono<Void> send(ByteBuf byteBuf, long sid) {
    return Mono.defer(
        () -> {
          // send with publisher (defer buffer cleanup to netty)
          return outbound
              .sendObject(Mono.just(byteBuf).map(TextWebSocketFrame::new))
              .then()
              .doOnError(
                  th -> {
                    Processor<ServiceMessage, ServiceMessage> processor =
                        inboundProcessors.remove(sid);
                    if (processor != null) {
                      processor.onError(th);
                    }
                  });
        });
  }

  /**
   * Close the websocket session with <i>normal</i> status. <a href="https://tools.ietf.org/html/rfc6455#section-7.4.1">Defined
   * Status Codes:</a> <i>1000 indicates a normal closure, meaning that the purpose for which the
   * connection was established has been fulfilled.</i>
   *
   * @return mono void
   */
  public Mono<Void> close() {
    return outbound.sendClose().then();
  }

  public Mono<Void> onClose() {
    return connection.onDispose();
  }

  private void handleResponse(
      ServiceMessage response,
      Consumer<ServiceMessage> onNext,
      Consumer<Throwable> onError,
      Runnable onComplete) {

    LOGGER.debug("Handle response: {}, session={}", response, id);

    try {
      Optional<Signal> signalOptional =
          Optional.ofNullable(response.header(SIGNAL)).map(Signal::from);

      if (signalOptional.isPresent()) {
        // handle completion signal
        Signal signal = signalOptional.get();
        if (signal == Signal.COMPLETE) {
          onComplete.run();
        }
        if (signal == Signal.ERROR) {
          // decode error data to retrieve real error cause
          ServiceMessage errorMessage = codec.decodeData(response, ErrorData.class);
          Throwable error = DefaultErrorMapper.INSTANCE.toError(errorMessage);
          String sid = response.header(STREAM_ID);
          LOGGER.error("Received error response: sid={}, error={}", sid, error);
          onError.accept(error);
        }
      } else {
        // handle normal response
        onNext.accept(response);
      }
    } catch (Exception e) {
      onError.accept(e);
    }
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", WebsocketSession.class.getSimpleName() + "[", "]")
        .add("id=" + id)
        .toString();
  }
}
