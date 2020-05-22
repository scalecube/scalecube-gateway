package io.scalecube.services.gateway.rsocket;

import io.rsocket.AbstractRSocket;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import io.scalecube.services.gateway.GatewaySession;
import io.scalecube.services.gateway.ReferenceCountUtil;
import io.scalecube.services.gateway.ServiceMessageCodec;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Extension class for rsocket. Holds gateway business logic in following methods: {@link
 * #fireAndForget(Payload)}, {@link #requestResponse(Payload)}, {@link #requestStream(Payload)} and
 * {@link #requestChannel(org.reactivestreams.Publisher)}.
 */
public final class RSocketGatewaySession extends AbstractRSocket implements GatewaySession {

  private static final AtomicLong SESSION_ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

  private final ServiceCall serviceCall;
  private final ServiceMessageCodec messageCodec;
  private final long sessionId;
  private final BiFunction<GatewaySession, ServiceMessage, ServiceMessage> messageMapper;

  /**
   * Constructor for gateway rsocket.
   *
   * @param serviceCall service call coming from microservices.
   * @param messageCodec message messageCodec.
   */
  public RSocketGatewaySession(
      ServiceCall serviceCall,
      ServiceMessageCodec messageCodec,
      BiFunction<GatewaySession, ServiceMessage, ServiceMessage> messageMapper) {
    this.serviceCall = serviceCall;
    this.messageCodec = messageCodec;
    this.messageMapper = messageMapper;
    this.sessionId = SESSION_ID_GENERATOR.incrementAndGet();
  }

  @Override
  public long sessionId() {
    return this.sessionId;
  }

  @Override
  public Map<String, List<String>> headers() {
    return Collections.emptyMap();
  }

  @Override
  public Mono<Void> fireAndForget(Payload payload) {
    return Mono.defer(() -> serviceCall.oneWay(toMessage(payload)));
  }

  @Override
  public Mono<Payload> requestResponse(Payload payload) {
    return Mono.defer(
        () -> {
          ServiceMessage request = toMessage(payload);
          return serviceCall
              .requestOne(request)
              .doOnError(th -> releaseRequestOnError(request))
              .onErrorResume(
                  th -> Mono.just(DefaultErrorMapper.INSTANCE.toMessage(request.qualifier(), th)))
              .map(this::toPayload);
        });
  }

  @Override
  public Flux<Payload> requestStream(Payload payload) {
    return Flux.defer(
        () -> {
          ServiceMessage request = toMessage(payload);
          return serviceCall
              .requestMany(request)
              .doOnError(th -> releaseRequestOnError(request))
              .onErrorResume(
                  th -> Mono.just(DefaultErrorMapper.INSTANCE.toMessage(request.qualifier(), th)))
              .map(this::toPayload);
        });
  }

  private ServiceMessage toMessage(Payload payload) {
    try {
      final ServiceMessage serviceMessage =
          messageCodec.decode(payload.sliceData().retain(), payload.sliceMetadata().retain());
      return messageMapper.apply(this, serviceMessage);
    } finally {
      payload.release();
    }
  }

  private Payload toPayload(ServiceMessage message) {
    return messageCodec.encodeAndTransform(message, ByteBufPayload::create);
  }

  private void releaseRequestOnError(ServiceMessage request) {
    ReferenceCountUtil.safestRelease(request.data());
  }

  @Override
  public String toString() {
    return "RSocketGatewaySession[" + sessionId + ']';
  }
}
