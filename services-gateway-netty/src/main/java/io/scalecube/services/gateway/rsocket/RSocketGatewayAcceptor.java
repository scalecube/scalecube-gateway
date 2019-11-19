package io.scalecube.services.gateway.rsocket;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.exceptions.ConnectionErrorException;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.GatewayMetrics;
import io.scalecube.services.gateway.ServiceMessageCodec;
import io.scalecube.services.gateway.SessionEventHandler;
import io.scalecube.services.transport.api.HeadersCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class RSocketGatewayAcceptor implements SocketAcceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGatewayAcceptor.class);

  private final ServiceCall serviceCall;
  private final GatewayMetrics metrics;
  private final SessionEventHandler<ServiceMessage> sessionEventHandler;

  /**
   * Creates new acceptor for RS gateway.
   *
   * @param serviceCall to call remote service
   * @param metrics to report events
   * @param sessionEventHandler handler for session events
   */
  public RSocketGatewayAcceptor(
      ServiceCall serviceCall,
      GatewayMetrics metrics,
      SessionEventHandler<ServiceMessage> sessionEventHandler) {
    this.serviceCall = serviceCall;
    this.metrics = metrics;
    this.sessionEventHandler = sessionEventHandler;
  }

  @Override
  public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket rsocket) {
    LOGGER.info("Accepted rsocket websocket: {}, connectionSetup: {}", rsocket, setup);

    // Prepare message codec together with headers from metainfo
    HeadersCodec headersCodec = HeadersCodec.getInstance(setup.metadataMimeType());
    ServiceMessageCodec messageCodec = new ServiceMessageCodec(headersCodec);
    final RSocketGatewaySession resultRsocketSession =
        new RSocketGatewaySession(
            serviceCall, metrics, messageCodec, sessionEventHandler::mapMessage);

    try {
      sessionEventHandler.onSessionOpen(resultRsocketSession);
    } catch (Exception e) {
      return Mono.error(new ConnectionErrorException(e.getMessage()));
    }

    rsocket
        .onClose()
        .doOnTerminate(
            () -> {
              LOGGER.info("Client disconnected: {}", rsocket);
              sessionEventHandler.onSessionClose(resultRsocketSession);
            })
        .subscribe(null, th -> LOGGER.error("Exception on closing rsocket: {}", th.toString()));

    return Mono.just(resultRsocketSession);
  }
}
