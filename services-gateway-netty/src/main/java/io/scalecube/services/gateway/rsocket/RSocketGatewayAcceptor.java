package io.scalecube.services.gateway.rsocket;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.GatewaySessionHandler;
import io.scalecube.services.gateway.ServiceMessageCodec;
import io.scalecube.services.transport.api.HeadersCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class RSocketGatewayAcceptor implements SocketAcceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGatewayAcceptor.class);

  private final ServiceCall serviceCall;
  private final GatewaySessionHandler<ServiceMessage> sessionHandler;

  /**
   * Creates new acceptor for RS gateway.
   *
   * @param serviceCall to call remote service
   * @param sessionHandler handler for session events
   */
  public RSocketGatewayAcceptor(
      ServiceCall serviceCall, GatewaySessionHandler<ServiceMessage> sessionHandler) {
    this.serviceCall = serviceCall;
    this.sessionHandler = sessionHandler;
  }

  @Override
  public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket rsocket) {
    LOGGER.info("Accepted rsocket websocket: {}, connectionSetup: {}", rsocket, setup);

    // Prepare message codec together with headers from metainfo
    HeadersCodec headersCodec = HeadersCodec.getInstance(setup.metadataMimeType());
    ServiceMessageCodec messageCodec = new ServiceMessageCodec(headersCodec);
    final RSocketGatewaySession gatewaySession =
        new RSocketGatewaySession(
            serviceCall,
            messageCodec,
            (session, req) -> sessionHandler.mapMessage(session, req, Context.empty()));
    sessionHandler.onSessionOpen(gatewaySession);
    rsocket
        .onClose()
        .doOnTerminate(
            () -> {
              LOGGER.info("Client disconnected: {}", rsocket);
              sessionHandler.onSessionClose(gatewaySession);
            })
        .subscribe(null, th -> LOGGER.error("Exception on closing rsocket: {}", th.toString()));

    return Mono.just(gatewaySession);
  }
}
