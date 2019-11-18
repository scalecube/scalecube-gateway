package io.scalecube.services.gateway.rsocket;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.exceptions.ConnectionErrorException;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.GatewayMetrics;
import io.scalecube.services.gateway.ServiceMessageCodec;
import io.scalecube.services.gateway.SessionEventsHandler;
import io.scalecube.services.transport.api.HeadersCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class RSocketGatewayAcceptor implements SocketAcceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGatewayAcceptor.class);

  private final ServiceCall serviceCall;
  private final GatewayMetrics metrics;
  private final SessionEventsHandler<ServiceMessage> sessionEventsHandler;

  public RSocketGatewayAcceptor(
      ServiceCall serviceCall,
      GatewayMetrics metrics,
      SessionEventsHandler<ServiceMessage> sessionEventsHandler) {
    this.serviceCall = serviceCall;
    this.metrics = metrics;
    this.sessionEventsHandler = sessionEventsHandler;
  }

  @Override
  public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket rsocket) {
    LOGGER.info("Accepted rsocket websocket: {}, connectionSetup: {}", rsocket, setup);

    // Prepare message codec together with headers from metainfo
    HeadersCodec headersCodec = HeadersCodec.getInstance(setup.metadataMimeType());
    ServiceMessageCodec messageCodec = new ServiceMessageCodec(headersCodec);
    final RSocketGatewaySession resultRsocketSession =
        new RSocketGatewaySession(
            serviceCall, metrics, messageCodec, sessionEventsHandler::mapMessage);

    try {
      sessionEventsHandler.onSessionOpen(resultRsocketSession);
    } catch (Exception e) {
      return Mono.error(new ConnectionErrorException(e.getMessage()));
    }

    rsocket
        .onClose()
        .doOnTerminate(
            () -> {
              LOGGER.info("Client disconnected: {}", rsocket);
              sessionEventsHandler.onSessionClose(resultRsocketSession);
            })
        .subscribe(null, th -> LOGGER.error("Exception on closing rsocket: {}", th.toString()));

    return Mono.just(resultRsocketSession);
  }
}
