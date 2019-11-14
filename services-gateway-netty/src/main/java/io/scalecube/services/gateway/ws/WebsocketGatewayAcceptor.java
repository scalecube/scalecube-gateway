package io.scalecube.services.gateway.ws;

import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import io.scalecube.services.gateway.GatewayMetrics;
import io.scalecube.services.gateway.ReferenceCountUtil;
import io.scalecube.services.gateway.SessionEventsHandler;
import io.scalecube.services.gateway.ws.GatewayMessage.Builder;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

public class WebsocketGatewayAcceptor
    implements BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> {

  private final GatewayMessageCodec messageCodec = new GatewayMessageCodec();
  private final ServiceCall serviceCall;
  private final GatewayMetrics metrics;
  private final SessionEventsHandler<String, GatewayMessage> gatewayHandler;

  /**
   * Constructor for websocket acceptor.
   *
   * @param serviceCall service call
   * @param gatewayHandler gateway handler
   */
  public WebsocketGatewayAcceptor(
      ServiceCall serviceCall, GatewayMetrics metrics, SessionEventsHandler<String, GatewayMessage> gatewayHandler) {
    this.serviceCall = Objects.requireNonNull(serviceCall, "serviceCall");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.gatewayHandler = Objects.requireNonNull(gatewayHandler, "gatewayHandler");
  }

  @Override
  public Publisher<Void> apply(HttpServerRequest httpRequest, HttpServerResponse httpResponse) {
    return httpResponse.sendWebsocket(
        (WebsocketInbound inbound, WebsocketOutbound outbound) ->
            onConnect(new WebsocketGatewaySession(messageCodec, httpRequest, inbound, outbound)));
  }

  private Mono<Void> onConnect(WebsocketGatewaySession session) {
    try {
      gatewayHandler.onSessionOpen(session.id());
    } catch (Exception e) {
      return session.close(e.getMessage());
    }

    session
        .receive()
        .subscribe(
            byteBuf ->
                Mono.fromCallable(() -> messageCodec.decode(byteBuf))
                    .doOnNext(message -> metrics.markRequest())
                    .map(this::checkSid)
                    .flatMap(msg -> handleCancel(session, msg))
                    .map(msg -> validateSid(session, (GatewayMessage) msg))
                    .map(this::checkQualifier)
                    .map(msg -> gatewayHandler.mapMessage(session.id(), msg))
                    .subscribe(
                        request -> handleMessage(session, request), th -> handleError(session, th)),
            th -> gatewayHandler.onError(session.id(), th, null, null));

    return session.onClose(() -> gatewayHandler.onSessionClose(session.id()));
  }

  private void handleMessage(WebsocketGatewaySession session, GatewayMessage request) {
    Long sid = request.streamId();

    AtomicBoolean receivedError = new AtomicBoolean(false);

    final Flux<ServiceMessage> serviceStream =
        serviceCall.requestMany(GatewayMessage.toServiceMessage(request));

    Disposable disposable =
        Optional.ofNullable(request.rateLimit())
            .map(serviceStream::limitRate)
            .orElse(serviceStream)
            .map(response -> prepareResponse(sid, response, receivedError))
            .doOnNext(response -> metrics.markServiceResponse())
            .doFinally(signalType -> session.dispose(sid))
            .subscribe(
                response ->
                    session
                        .send(response)
                        .subscribe(
                            avoid -> metrics.markResponse(),
                            th -> gatewayHandler.onError(session.id(), th, request, response)),
                th -> handleError(session, request, th),
                () -> handleCompletion(session, request, receivedError));

    session.register(sid, disposable);
  }

  private void handleError(WebsocketGatewaySession session, Throwable throwable) {
    if (throwable instanceof WebsocketRequestException) {
      WebsocketRequestException ex = (WebsocketRequestException) throwable;
      ex.releaseRequest(); // release
      handleError(session, ex.request(), ex.getCause());
    } else {
      gatewayHandler.onError(session.id(), throwable, null, null);
    }
  }

  private void handleError(WebsocketGatewaySession session, GatewayMessage req, Throwable th) {
    gatewayHandler.onError(session.id(), th, req, null);

    Builder builder = GatewayMessage.from(DefaultErrorMapper.INSTANCE.toMessage(th));
    Optional.ofNullable(req.streamId()).ifPresent(builder::streamId);
    GatewayMessage response = builder.signal(Signal.ERROR).build();

    session
        .send(response)
        .subscribe(null, ex -> gatewayHandler.onError(session.id(), ex, req, response));
  }

  private void handleCompletion(
      WebsocketGatewaySession session, GatewayMessage req, AtomicBoolean receivedError) {
    if (!receivedError.get()) {
      Builder builder = GatewayMessage.builder();
      Optional.ofNullable(req.streamId()).ifPresent(builder::streamId);
      GatewayMessage response = builder.signal(Signal.COMPLETE).build();
      session.send(response).subscribe(null, ex -> gatewayHandler.onError(session.id(), ex, req, null));
    }
  }

  private GatewayMessage checkQualifier(GatewayMessage msg) {
    if (msg.qualifier() == null) {
      throw WebsocketRequestException.newBadRequest("qualifier is missing", msg);
    }
    return msg;
  }

  private GatewayMessage validateSid(WebsocketGatewaySession session, GatewayMessage msg) {
    if (session.containsSid(msg.streamId())) {
      throw WebsocketRequestException.newBadRequest(
          "sid=" + msg.streamId() + " is already registered", msg);
    } else {
      return msg;
    }
  }

  private Mono<?> handleCancel(WebsocketGatewaySession session, GatewayMessage msg) {
    if (!msg.hasSignal(Signal.CANCEL)) {
      return Mono.just(msg);
    }

    // release data if CANCEL contains data (it shouldn't normally), just in case
    Optional.ofNullable(msg.data()).ifPresent(ReferenceCountUtil::safestRelease);

    // dispose by sid (if anything to dispose)
    session.dispose(msg.streamId());

    GatewayMessage cancelAck =
        GatewayMessage.builder().streamId(msg.streamId()).signal(Signal.CANCEL).build();
    return session.send(cancelAck); // no need to subscribe here since flatMap will do
  }

  private GatewayMessage checkSid(GatewayMessage msg) {
    if (msg.streamId() == null) {
      throw WebsocketRequestException.newBadRequest("sid is missing", msg);
    } else {
      return msg;
    }
  }

  private GatewayMessage prepareResponse(
      Long streamId, ServiceMessage message, AtomicBoolean receivedErrorMessage) {
    GatewayMessage.Builder response = GatewayMessage.from(message).streamId(streamId);
    if (message.isError()) {
      receivedErrorMessage.set(true);
      response.signal(Signal.ERROR);
    }
    return response.build();
  }
}
