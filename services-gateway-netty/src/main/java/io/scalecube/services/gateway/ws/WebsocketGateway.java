package io.scalecube.services.gateway.ws;

import io.netty.channel.EventLoopGroup;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.gateway.Gateway;
import io.scalecube.services.gateway.GatewayLoopResources;
import io.scalecube.services.gateway.GatewayOptions;
import io.scalecube.services.gateway.GatewayTemplate;
import io.scalecube.services.gateway.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.resources.LoopResources;

public class WebsocketGateway extends GatewayTemplate {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketGateway.class);

  private DisposableServer server;
  private LoopResources loopResources;
  private Consumer<WebsocketSession> onOpen = this::defaultOnSessionOpen;
  private Consumer<WebsocketSession> onClose = this::defaultOnSessionClose;

  /**
   * Construcotr.
   *
   * @param options options
   */
  public WebsocketGateway(GatewayOptions options) {
    super(options);
  }

  /**
   * Construcotor.
   *
   * @param options options
   * @param onOpen opOpen function
   * @param onClose onCLose function
   */
  public WebsocketGateway(
      GatewayOptions options,
      Consumer<WebsocketSession> onOpen,
      Consumer<WebsocketSession> onClose) {
    super(options);
    this.onOpen = onOpen;
    this.onClose = onClose;
  }

  @Override
  public Mono<Gateway> start() {
    return Mono.defer(
        () -> {
          ServiceCall serviceCall =
              options.call().requestReleaser(ReferenceCountUtil::safestRelease);
          WebsocketGatewayAcceptor acceptor =
              new WebsocketGatewayAcceptor(serviceCall, onOpen, onClose, gatewayMetrics);

          if (options.workerPool() != null) {
            loopResources = new GatewayLoopResources((EventLoopGroup) options.workerPool());
          } else {
            loopResources = LoopResources.create("ws-gateway");
          }

          return prepareHttpServer(loopResources, options.port(), gatewayMetrics)
              .handle(acceptor)
              .bind()
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
    return shutdownServer(server).then(shutdownLoopResources(loopResources));
  }

  private void defaultOnSessionOpen(WebsocketSession session) {
    LOGGER.info("Session opened: " + session);
  }

  private void defaultOnSessionClose(WebsocketSession session) {
    LOGGER.info("Session closed: " + session);
  }
}
