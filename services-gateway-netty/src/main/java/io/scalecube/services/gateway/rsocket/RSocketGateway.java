package io.scalecube.services.gateway.rsocket;

import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.WebsocketServerTransport;
import io.scalecube.net.Address;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import io.scalecube.services.exceptions.ServiceProviderErrorMapper;
import io.scalecube.services.gateway.Gateway;
import io.scalecube.services.gateway.GatewayOptions;
import io.scalecube.services.gateway.GatewaySessionHandler;
import io.scalecube.services.gateway.GatewayTemplate;
import java.net.InetSocketAddress;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

public class RSocketGateway extends GatewayTemplate {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGateway.class);

  private final GatewaySessionHandler sessionHandler;
  private final ServiceProviderErrorMapper errorMapper;

  private CloseableChannel server;
  private LoopResources loopResources;

  public RSocketGateway(GatewayOptions options) {
    this(options, GatewaySessionHandler.DEFAULT_INSTANCE, DefaultErrorMapper.INSTANCE);
  }

  public RSocketGateway(GatewayOptions options, GatewaySessionHandler sessionHandler) {
    this(options, sessionHandler, DefaultErrorMapper.INSTANCE);
  }

  /**
   * Constructor.
   *
   * @param options gateway options
   * @param sessionHandler session handler
   * @param errorMapper error mapper
   */
  public RSocketGateway(
      GatewayOptions options,
      GatewaySessionHandler sessionHandler,
      ServiceProviderErrorMapper errorMapper) {
    super(options);
    this.sessionHandler = sessionHandler;
    this.errorMapper = errorMapper;
  }

  @Override
  public Mono<Gateway> start() {
    return Mono.defer(
        () -> {
          RSocketGatewayAcceptor acceptor =
              new RSocketGatewayAcceptor(options.call(), sessionHandler, errorMapper);

          loopResources = LoopResources.create("rsocket-gateway");

          WebsocketServerTransport rsocketTransport =
              WebsocketServerTransport.create(prepareHttpServer(loopResources, options.port()));

          return RSocketServer.create()
              .acceptor(acceptor)
              .payloadDecoder(PayloadDecoder.DEFAULT)
              .bind(rsocketTransport)
              .doOnSuccess(server -> this.server = server)
              .thenReturn(this);
        });
  }

  @Override
  public Address address() {
    InetSocketAddress address = server.address();
    return Address.create(address.getHostString(), address.getPort());
  }

  @Override
  public Mono<Void> stop() {
    return Flux.concatDelayError(shutdownServer(), shutdownLoopResources(loopResources)).then();
  }

  private Mono<Void> shutdownServer() {
    return Mono.defer(
        () -> {
          if (server == null) {
            return Mono.empty();
          }
          server.dispose();
          return server.onClose().doOnError(e -> LOGGER.warn("Failed to close server: " + e));
        });
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", RSocketGateway.class.getSimpleName() + "[", "]")
        .add("server=" + server)
        .add("loopResources=" + loopResources)
        .add("options=" + options)
        .toString();
  }
}
