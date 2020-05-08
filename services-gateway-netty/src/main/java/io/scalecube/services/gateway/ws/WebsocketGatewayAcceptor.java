package io.scalecube.services.gateway.ws;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import io.scalecube.services.gateway.GatewayMetrics;
import io.scalecube.services.gateway.GatewaySessionHandler;
import io.scalecube.services.gateway.ReferenceCountUtil;
import io.scalecube.services.gateway.ws.GatewayMessage.Builder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import reactor.util.context.Context;

public class WebsocketGatewayAcceptor
    implements BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> {

  public static final int DEFAULT_ERROR_CODE = 500;

  private final GatewayMessageCodec messageCodec = new GatewayMessageCodec();
  private final ServiceCall serviceCall;
  private final GatewayMetrics metrics;
  private final GatewaySessionHandler<GatewayMessage> gatewayHandler;

  /**
   * Constructor for websocket acceptor.
   *
   * @param serviceCall service call
   * @param gatewayHandler gateway handler
   */
  public WebsocketGatewayAcceptor(
      ServiceCall serviceCall,
      GatewayMetrics metrics,
      GatewaySessionHandler<GatewayMessage> gatewayHandler) {
    this.serviceCall = Objects.requireNonNull(serviceCall, "serviceCall");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.gatewayHandler = Objects.requireNonNull(gatewayHandler, "gatewayHandler");
  }

  @Override
  public Publisher<Void> apply(HttpServerRequest httpRequest, HttpServerResponse httpResponse) {
    Map<String, List<String>> headers = computeHeaders(httpRequest.requestHeaders());
    return gatewayHandler
        .onConnectionOpen(headers)
        .doOnError(throwable -> httpResponse.status(DEFAULT_ERROR_CODE).send().subscribe())
        .then(
            Mono.defer(
                () ->
                    httpResponse.sendWebsocket(
                        (WebsocketInbound inbound, WebsocketOutbound outbound) ->
                            onConnect(
                                new WebsocketGatewaySession(
                                    messageCodec, headers, inbound, outbound, gatewayHandler)))))
        .onErrorResume(throwable -> Mono.empty());
  }

  private static Map<String, List<String>> computeHeaders(HttpHeaders httpHeaders) {
    Map<String, List<String>> headers = new HashMap<>();
    for (String name : httpHeaders.names()) {
      headers.put(name, httpHeaders.getAll(name));
    }
    return headers;
  }

  private Mono<Void> onConnect(WebsocketGatewaySession session) {
    gatewayHandler.onSessionOpen(session);

    session
        .receive()
        .doOnError(th -> gatewayHandler.onSessionError(session, th))
        .subscribe(
            byteBuf ->
                Mono.deferWithContext(context -> onRequest(session, byteBuf, context))
                    .subscriberContext(
                        context -> gatewayHandler.onRequest(session, byteBuf, context))
                    .subscribe());

    return session.onClose(() -> gatewayHandler.onSessionClose(session));
  }

  private Mono<GatewayMessage> onRequest(
      WebsocketGatewaySession session, ByteBuf byteBuf, Context context) {
    return Mono.fromCallable(() -> messageCodec.decode(byteBuf))
        .doOnNext(message -> metrics.markRequest())
        .map(this::validateSid)
        .flatMap(msg -> onCancel(session, msg))
        .map(msg -> validateSid(session, (GatewayMessage) msg))
        .map(this::validateQualifier)
        .map(msg -> gatewayHandler.mapMessage(session, msg, context))
        .doOnNext(request -> onMessage(session, request, context))
        .doOnError(
            th -> {
              if (!(th instanceof WebsocketContextException)) {
                // decode failed at this point
                gatewayHandler.onError(session, th, context);
                return;
              }

              WebsocketContextException wex = (WebsocketContextException) th;
              wex.releaseRequest(); // release

              onError(session, wex.request(), wex.getCause(), context);
            });
  }

  private void onMessage(WebsocketGatewaySession session, GatewayMessage request, Context context) {
    final Long sid = request.streamId();
    final AtomicBoolean receivedError = new AtomicBoolean(false);

    final Flux<ServiceMessage> serviceStream =
        serviceCall.requestMany(GatewayMessage.toServiceMessage(request));

    Disposable disposable =
        Optional.ofNullable(request.rateLimit())
            .map(serviceStream::limitRate)
            .orElse(serviceStream)
            .map(response -> prepareResponse(sid, response, receivedError))
            .doOnNext(response -> metrics.markServiceResponse())
            .flatMap(session::send)
            .doOnError(th -> onError(session, request, th, context))
            .doOnComplete(() -> onComplete(session, request, receivedError, context))
            .doFinally(signalType -> session.dispose(sid))
            .subscriberContext(context)
            .subscribe();

    session.register(sid, disposable);
  }

  private void onError(
      WebsocketGatewaySession session, GatewayMessage req, Throwable th, Context context) {

    Builder builder = GatewayMessage.from(DefaultErrorMapper.INSTANCE.toMessage(th));
    Optional.ofNullable(req.streamId()).ifPresent(builder::streamId);
    GatewayMessage response = builder.signal(Signal.ERROR).build();

    session.send(response).subscriberContext(context).subscribe();
  }

  private void onComplete(
      WebsocketGatewaySession session,
      GatewayMessage req,
      AtomicBoolean receivedError,
      Context context) {

    if (!receivedError.get()) {
      Builder builder = GatewayMessage.builder();
      Optional.ofNullable(req.streamId()).ifPresent(builder::streamId);
      GatewayMessage response = builder.signal(Signal.COMPLETE).build();

      session.send(response).subscriberContext(context).subscribe();
    }
  }

  private Mono<?> onCancel(WebsocketGatewaySession session, GatewayMessage msg) {
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

  private GatewayMessage validateQualifier(GatewayMessage msg) {
    if (msg.qualifier() == null) {
      throw WebsocketContextException.badRequest("qualifier is missing", msg);
    }
    return msg;
  }

  private GatewayMessage validateSid(WebsocketGatewaySession session, GatewayMessage msg) {
    if (session.containsSid(msg.streamId())) {
      throw WebsocketContextException.badRequest(
          "sid=" + msg.streamId() + " is already registered", msg);
    } else {
      return msg;
    }
  }

  private GatewayMessage validateSid(GatewayMessage msg) {
    if (msg.streamId() == null) {
      throw WebsocketContextException.badRequest("sid is missing", msg);
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
