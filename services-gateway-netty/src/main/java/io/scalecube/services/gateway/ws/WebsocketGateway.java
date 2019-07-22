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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.resources.LoopResources;

public class WebsocketGateway extends GatewayTemplate {

  private BiFunction<WebsocketSession, GatewayMessage, GatewayMessage> onMessage;
  private Consumer<WebsocketSession> onOpen;
  private Consumer<WebsocketSession> onClose;

  private DisposableServer server;
  private LoopResources loopResources;

  /**
   * Constructor.
   *
   * @param options options
   */
  public WebsocketGateway(GatewayOptions options) {
    super(options);
  }

  /**
   * Constructor.
   *
   * @param options options
   * @param onMessage onMessage function
   * @param onOpen opOpen function
   * @param onClose onCLose function
   */
  public WebsocketGateway(
      GatewayOptions options,
      BiFunction<WebsocketSession, GatewayMessage, GatewayMessage> onMessage,
      Consumer<WebsocketSession> onOpen,
      Consumer<WebsocketSession> onClose) {
    super(options);
    this.onMessage = onMessage;
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
              new WebsocketGatewayAcceptor(serviceCall, gatewayMetrics, onMessage, onOpen, onClose);

          if (options.workerPool() != null) {
            loopResources = new GatewayLoopResources((EventLoopGroup) options.workerPool());
          } else {
            loopResources = LoopResources.create("websocket-gateway");
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
    return Flux.concatDelayError(shutdownServer(server), shutdownLoopResources(loopResources))
        .then();
  }
}
