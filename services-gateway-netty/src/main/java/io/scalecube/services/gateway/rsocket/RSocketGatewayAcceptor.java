package io.scalecube.services.gateway.rsocket;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.GatewayMetrics;
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
  private final GatewayMetrics metrics;
  private final GatewaySessionHandler<ServiceMessage> gatewaySessionHandler;

  /**
   * Creates new acceptor for RS gateway.
   *
   * @param serviceCall to call remote service
   * @param metrics to report events
   * @param gatewaySessionHandler handler for session events
   */
  public RSocketGatewayAcceptor(
      ServiceCall serviceCall,
      GatewayMetrics metrics,
      GatewaySessionHandler<ServiceMessage> gatewaySessionHandler) {
    this.serviceCall = serviceCall;
    this.metrics = metrics;
    this.gatewaySessionHandler = gatewaySessionHandler;
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
            metrics,
            messageCodec,
            (session, req) -> gatewaySessionHandler.mapMessage(session, req, Context.empty()));
    gatewaySessionHandler.onSessionOpen(gatewaySession);
    rsocket
        .onClose()
        .doOnTerminate(
            () -> {
              LOGGER.info("Client disconnected: {}", rsocket);
              gatewaySessionHandler.onSessionClose(gatewaySession);
            })
        .subscribe(null, th -> LOGGER.error("Exception on closing rsocket: {}", th.toString()));

    return Mono.just(gatewaySession);
  }
}
