package io.scalecube.services.gateway.transport.websocket;

import static reactor.core.publisher.Sinks.EmitResult.FAIL_NON_SERIALIZED;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.scalecube.services.api.ErrorData;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.transport.api.ReferenceCountUtil;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.jctools.maps.NonBlockingHashMapLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitResult;
import reactor.netty.Connection;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

public final class WebsocketGatewayClientSession {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketGatewayClientSession.class);

  private static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION =
      new ClosedChannelException();

  private static final String STREAM_ID = "sid";
  private static final String SIGNAL = "sig";

  private final String id; // keep id for tracing
  private final GatewayClientCodec<ByteBuf> codec;
  private final Connection connection;

  // processor by sid mapping
  private final Map<Long, Object> inboundProcessors = new NonBlockingHashMapLong<>(1024);

  WebsocketGatewayClientSession(GatewayClientCodec<ByteBuf> codec, Connection connection) {
    this.id = Integer.toHexString(System.identityHashCode(this));
    this.codec = codec;
    this.connection = connection;

    WebsocketInbound inbound = (WebsocketInbound) connection.inbound();
    inbound
        .receive()
        .retain()
        .subscribe(
            byteBuf -> {
              if (!byteBuf.isReadable()) {
                ReferenceCountUtil.safestRelease(byteBuf);
                return;
              }

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
              Object processor = inboundProcessors.get(sid);
              if (processor == null) {
                Optional.ofNullable(message.data()).ifPresent(ReferenceCountUtil::safestRelease);
                return;
              }

              // handle response message
              handleResponse(message, processor);
            });

    connection.onDispose(
        () -> inboundProcessors.forEach((k, o) -> emitError(o, CLOSED_CHANNEL_EXCEPTION)));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  <T> Sinks.One<T> newMonoProcessor(long sid) {
    return (Sinks.One)
        inboundProcessors.computeIfAbsent(
            sid,
            key -> {
              LOGGER.debug("Put sid={}, session={}", sid, id);
              return Sinks.one();
            });
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  <T> Sinks.Many<T> newUnicastProcessor(long sid) {
    return (Sinks.Many)
        inboundProcessors.computeIfAbsent(
            sid,
            key -> {
              LOGGER.debug("Put sid={}, session={}", sid, id);
              return Sinks.many().unicast().onBackpressureBuffer();
            });
  }

  void removeProcessor(long sid) {
    if (inboundProcessors.remove(sid) != null) {
      LOGGER.debug("Removed sid={}, session={}", sid, id);
    }
  }

  Mono<Void> send(ByteBuf byteBuf) {
    return Mono.defer(
        () -> {
          // send with publisher (defer buffer cleanup to netty)
          return connection
              .outbound()
              .sendObject(Mono.just(byteBuf).map(TextWebSocketFrame::new), f -> true)
              .then();
        });
  }

  void cancel(long sid, String qualifier) {
    ByteBuf byteBuf =
        codec.encode(
            ServiceMessage.builder()
                .qualifier(qualifier)
                .header(STREAM_ID, sid)
                .header(SIGNAL, Signal.CANCEL.codeAsString())
                .build());

    send(byteBuf)
        .subscribe(
            null,
            th ->
                LOGGER.error("Exception occurred on sending CANCEL signal for session={}", id, th));
  }

  /**
   * Close the websocket session with <i>normal</i> status. <a
   * href="https://tools.ietf.org/html/rfc6455#section-7.4.1">Defined Status Codes:</a> <i>1000
   * indicates a normal closure, meaning that the purpose for which the connection was established
   * has been fulfilled.</i>
   *
   * @return mono void
   */
  public Mono<Void> close() {
    return ((WebsocketOutbound) connection.outbound()).sendClose().then();
  }

  public Mono<Void> onClose() {
    return connection.onDispose();
  }

  private void handleResponse(ServiceMessage response, Object processor) {
    LOGGER.debug("Handle response: {}, session={}", response, id);

    try {
      Optional<Signal> signalOptional =
          Optional.ofNullable(response.header(SIGNAL)).map(Signal::from);

      if (!signalOptional.isPresent()) {
        // handle normal response
        emitNext(processor, response);
      } else {
        // handle completion signal
        Signal signal = signalOptional.get();
        if (signal == Signal.COMPLETE) {
          emitComplete(processor);
        }
        if (signal == Signal.ERROR) {
          // decode error data to retrieve real error cause
          ServiceMessage errorMessage = codec.decodeData(response, ErrorData.class);
          emitNext(processor, errorMessage);
        }
      }
    } catch (Exception e) {
      emitError(processor, e);
    }
  }

  private static void emitNext(Object processor, ServiceMessage message) {
    if (processor instanceof Sinks.One) {
      //noinspection unchecked
      ((Sinks.One<ServiceMessage>) processor)
          .emitValue(message, EmitFailureHandler.RETRY_NOT_SERIALIZED);
    }
    if (processor instanceof Sinks.Many) {
      //noinspection unchecked
      ((Sinks.Many<ServiceMessage>) processor)
          .emitNext(message, EmitFailureHandler.RETRY_NOT_SERIALIZED);
    }
  }

  private static void emitComplete(Object processor) {
    if (processor instanceof Sinks.One) {
      ((Sinks.One<?>) processor).emitEmpty(EmitFailureHandler.RETRY_NOT_SERIALIZED);
    }
    if (processor instanceof Sinks.Many) {
      ((Sinks.Many<?>) processor).emitComplete(EmitFailureHandler.RETRY_NOT_SERIALIZED);
    }
  }

  private static void emitError(Object processor, Exception e) {
    if (processor instanceof Sinks.One) {
      ((Sinks.One<?>) processor).emitError(e, EmitFailureHandler.RETRY_NOT_SERIALIZED);
    }
    if (processor instanceof Sinks.Many) {
      ((Sinks.Many<?>) processor).emitError(e, EmitFailureHandler.RETRY_NOT_SERIALIZED);
    }
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", WebsocketGatewayClientSession.class.getSimpleName() + "[", "]")
        .add("id=" + id)
        .toString();
  }

  private static class EmitFailureHandler implements Sinks.EmitFailureHandler {

    private static final EmitFailureHandler RETRY_NOT_SERIALIZED = new EmitFailureHandler();

    @Override
    public boolean onEmitFailure(SignalType signalType, EmitResult emitResult) {
      return emitResult == FAIL_NON_SERIALIZED;
    }
  }
}
