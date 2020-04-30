package io.scalecube.services.gateway.ws;

import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.scalecube.net.Address;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.gateway.Gateway;
import io.scalecube.services.gateway.GatewayOptions;
import io.scalecube.services.gateway.GatewaySessionHandler;
import io.scalecube.services.gateway.GatewayTemplate;
import io.scalecube.services.gateway.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.resources.LoopResources;

public class WebsocketGateway extends GatewayTemplate {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketGateway.class);

  private final GatewaySessionHandler<GatewayMessage> gatewayHandler;
  private final Duration keepAliveInterval;

  private DisposableServer server;
  private LoopResources loopResources;

  public WebsocketGateway(GatewayOptions options) {
    this(options, Duration.ZERO, GatewaySessionHandler.DEFAULT_WS_INSTANCE);
  }

  public WebsocketGateway(GatewayOptions options, Duration keepAliveInterval) {
    this(options, keepAliveInterval, GatewaySessionHandler.DEFAULT_WS_INSTANCE);
  }

  public WebsocketGateway(
      GatewayOptions options, GatewaySessionHandler<GatewayMessage> gatewayHandler) {
    this(options, Duration.ZERO, gatewayHandler);
  }

  /**
   * Constructor.
   *
   * @param options gateway options
   * @param keepAliveInterval keep alive interval
   * @param gatewayHandler gateway handler
   */
  public WebsocketGateway(
      GatewayOptions options,
      Duration keepAliveInterval,
      GatewaySessionHandler<GatewayMessage> gatewayHandler) {
    super(options);
    this.keepAliveInterval = keepAliveInterval;
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
              .tcpConfiguration(tcpServer -> tcpServer.doOnConnection(this::setupKeepAlive))
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

  private void setupKeepAlive(Connection connection) {
    if (keepAliveInterval != Duration.ZERO) {
      connection
          .onReadIdle(keepAliveInterval.toMillis(), () -> onReadIdle(connection))
          .onWriteIdle(keepAliveInterval.toMillis(), () -> onWriteIdle(connection));
    }
  }

  private void onWriteIdle(Connection connection) {
    LOGGER.debug("Sending keepalive on writeIdle");
    connection
        .outbound()
        .sendObject(new PingWebSocketFrame())
        .then()
        .subscribe(null, ex -> LOGGER.warn("Can't send keepalive on writeIdle: " + ex));
  }

  private void onReadIdle(Connection connection) {
    LOGGER.debug("Sending keepalive on readIdle");
    connection
        .outbound()
        .sendObject(new PingWebSocketFrame())
        .then()
        .subscribe(null, ex -> LOGGER.warn("Can't send keepalive on readIdle: " + ex));
  }
}
