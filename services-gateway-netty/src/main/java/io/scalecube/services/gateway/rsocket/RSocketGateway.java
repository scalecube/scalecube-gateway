package io.scalecube.services.gateway.rsocket;

import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.WebsocketServerTransport;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.Gateway;
import io.scalecube.services.gateway.GatewayOptions;
import io.scalecube.services.gateway.GatewaySessionHandler;
import io.scalecube.services.gateway.GatewayTemplate;
import io.scalecube.services.gateway.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

public class RSocketGateway extends GatewayTemplate {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGateway.class);

  private final GatewaySessionHandler<ServiceMessage> gatewaySessionHandler;
  private CloseableChannel server;
  private LoopResources loopResources;

  public RSocketGateway(GatewayOptions options) {
    super(options);
    this.gatewaySessionHandler = GatewaySessionHandler.DEFAULT_RS_INSTANCE;
  }

  public RSocketGateway(
      GatewayOptions options, GatewaySessionHandler<ServiceMessage> gatewaySessionHandler) {
    super(options);
    this.gatewaySessionHandler = gatewaySessionHandler;
  }

  @Override
  public Mono<Gateway> start() {
    return Mono.defer(
        () -> {
          ServiceCall serviceCall =
              options.call().requestReleaser(ReferenceCountUtil::safestRelease);
          RSocketGatewayAcceptor acceptor =
              new RSocketGatewayAcceptor(serviceCall, gatewayMetrics, gatewaySessionHandler);

          loopResources = LoopResources.create("rsocket-gateway");

          WebsocketServerTransport rsocketTransport =
              WebsocketServerTransport.create(
                  prepareHttpServer(loopResources, options.port(), gatewayMetrics));

          return RSocketServer.create()
              .acceptor(acceptor)
              .payloadDecoder(PayloadDecoder.DEFAULT)
              .errorConsumer(th -> LOGGER.warn("Exception occurred at rsocket gateway: " + th))
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
