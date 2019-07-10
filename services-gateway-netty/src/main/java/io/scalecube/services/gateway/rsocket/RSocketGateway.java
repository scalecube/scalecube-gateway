package io.scalecube.services.gateway.rsocket;

import io.netty.channel.EventLoopGroup;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocketFactory;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.WebsocketServerTransport;
import io.rsocket.util.ByteBufPayload;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.gateway.Gateway;
import io.scalecube.services.gateway.GatewayLoopResources;
import io.scalecube.services.gateway.GatewayOptions;
import io.scalecube.services.gateway.GatewayTemplate;
import io.scalecube.services.gateway.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Consumer;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

public class RSocketGateway extends GatewayTemplate {

  private CloseableChannel server;
  private LoopResources loopResources;
  private Consumer<ConnectionSetupPayload> onOpen;
  private Runnable onClose;

  /**
   * Creates new Rsocket gateway.
   *
   * @param options gateway options
   */
  public RSocketGateway(GatewayOptions options) {
    super(options);
  }

  /**
   * Creates new Rsocket gateway.
   *
   * @param options gateway options
   * @param onOpen on connection setup handler
   * @param onClose on connection close handler
   */
  public RSocketGateway(
      GatewayOptions options, Consumer<ConnectionSetupPayload> onOpen, Runnable onClose) {
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
          RSocketGatewayAcceptor acceptor =
              new RSocketGatewayAcceptor(serviceCall, gatewayMetrics, onOpen, onClose);

          if (options.workerPool() != null) {
            loopResources = new GatewayLoopResources((EventLoopGroup) options.workerPool());
          } else {
            loopResources = LoopResources.create("rsocket-gateway");
          }

          WebsocketServerTransport rsocketTransport =
              WebsocketServerTransport.create(
                  prepareHttpServer(loopResources, options.port(), gatewayMetrics));

          return RSocketFactory.receive()
              .frameDecoder(
                  frame ->
                      ByteBufPayload.create(
                          frame.sliceData().retain(), frame.sliceMetadata().retain()))
              .acceptor(acceptor)
              .transport(rsocketTransport)
              .start()
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

  private Mono<Void> shutdownServer(CloseableChannel closeableChannel) {
    return Mono.defer(
        () ->
            Optional.ofNullable(closeableChannel)
                .map(
                    server -> {
                      server.dispose();
                      return server.onClose().onErrorResume(e -> Mono.empty());
                    })
                .orElse(Mono.empty()));
  }
}
