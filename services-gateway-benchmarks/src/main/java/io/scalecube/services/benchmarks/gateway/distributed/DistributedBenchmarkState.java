package io.scalecube.services.benchmarks.gateway.distributed;

import io.scalecube.benchmarks.BenchmarkSettings;
import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.benchmarks.gateway.AbstractBenchmarkState;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.examples.BenchmarkServiceImpl;
import io.scalecube.services.gateway.http.HttpGateway;
import io.scalecube.services.gateway.rsocket.RSocketGateway;
import io.scalecube.services.gateway.ws.WebsocketGateway;
import io.scalecube.services.transport.gw.client.GatewayClient;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import java.util.function.BiFunction;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

public class DistributedBenchmarkState extends AbstractBenchmarkState<DistributedBenchmarkState> {

  private final String gatewayName;

  private Microservices services;
  private Microservices gateway;

  public DistributedBenchmarkState(
      BenchmarkSettings settings,
      String gatewayName,
      Function<Address, GatewayClient> clientBuilder) {
    super(settings, clientBuilder);
    this.gatewayName = gatewayName;
  }

  @Override
  protected void beforeAll() throws Exception {
    super.beforeAll();

    gateway =
        Microservices.builder()
            .gateway(opts -> new RSocketGateway(opts.id("rsws")))
            .gateway(opts -> new WebsocketGateway(opts.id("ws")))
            .gateway(opts -> new HttpGateway(opts.id("http")))
            .discovery(ScalecubeServiceDiscovery::new)
            .transport(RSocketServiceTransport::new)
            .metrics(registry())
            .startAwait();

    Address seedAddress = gateway.discovery().address();
    int numOfThreads = Runtime.getRuntime().availableProcessors();
    services =
        Microservices.builder()
            .discovery(
                serviceEndpoint ->
                    new ScalecubeServiceDiscovery(serviceEndpoint)
                        .options(opts -> opts.seedMembers(seedAddress)))
            .transport(() -> new RSocketServiceTransport().numOfWorkers(numOfThreads))
            .services(new BenchmarkServiceImpl())
            .startAwait();
  }

  @Override
  protected void afterAll() throws Exception {
    super.afterAll();
    if (services != null) {
      services.shutdown().block();
    }
    if (gateway != null) {
      gateway.shutdown().block();
    }
  }

  @Override
  public Mono<GatewayClient> createClient() {
    return createClient(gateway, gatewayName, clientBuilder);
  }
}
