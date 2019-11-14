package io.scalecube.services.gateway.ws;

import io.scalecube.net.Address;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.gateway.Gateway;
import io.scalecube.services.gateway.GatewayOptions;
import io.scalecube.services.gateway.GatewayTemplate;
import io.scalecube.services.gateway.ReferenceCountUtil;
import io.scalecube.services.gateway.SessionEventsHandler;
import java.net.InetSocketAddress;
import java.util.StringJoiner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.resources.LoopResources;

public class WebsocketGateway extends GatewayTemplate {

  private final SessionEventsHandler<String, GatewayMessage> gatewayHandler;

  private DisposableServer server;
  private LoopResources loopResources;

  /**
   * Constructor.
   *
   * @param options options
   */
  public WebsocketGateway(GatewayOptions options) {
    super(options);
    this.gatewayHandler = SessionEventsHandler.DEFAULT_WS_INSTANCE;
  }

  /**
   * Constructor.
   *
   * @param options options
   * @param gatewayHandler gateway handler
   */
  public WebsocketGateway(GatewayOptions options, SessionEventsHandler<String, GatewayMessage> gatewayHandler) {
    super(options);
    this.gatewayHandler = gatewayHandler;
  }

  @Override
  public Mono<Gateway> start() {
    return Mono.defer(
        () -> {
          ServiceCall serviceCall =
              options.call().requestReleaser(ReferenceCountUtil::safestRelease);
          WebsocketGatewayAcceptor acceptor =
              new WebsocketGatewayAcceptor(serviceCall, gatewayMetrics, gatewayHandler);

          loopResources = LoopResources.create("websocket-gateway");

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

  @Override
  public String toString() {
    return new StringJoiner(", ", WebsocketGateway.class.getSimpleName() + "[", "]")
        .add("server=" + server)
        .add("loopResources=" + loopResources)
        .add("options=" + options)
        .toString();
  }
}
