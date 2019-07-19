package io.scalecube.services.benchmarks.gateway.remote;

import io.scalecube.benchmarks.BenchmarkSettings;
import io.scalecube.net.Address;
import io.scalecube.services.benchmarks.gateway.AbstractBenchmarkState;
import io.scalecube.services.transport.gw.client.GatewayClient;
import java.util.function.Function;
import reactor.core.publisher.Mono;

public class RemoteBenchmarkState extends AbstractBenchmarkState<RemoteBenchmarkState> {

  public static final int WS_PORT = 7070;
  public static final int RS_PORT = 9090;
  public static final int HTTP_PORT = 8080;

  private final Address gatewayAddress;

  /**
   * Constructor for benchmark state.
   *
   * @param settings benchmark settings.
   */
  public RemoteBenchmarkState(
      BenchmarkSettings settings, int gatewayPort, Function<Address, GatewayClient> clientBuilder) {
    super(settings, clientBuilder);
    gatewayAddress = Address.create(settings.find("gatewayHost", "localhost"), gatewayPort);
  }

  /**
   * Factory function for {@link GatewayClient}.
   *
   * @return client
   */
  public Mono<GatewayClient> createClient() {
    return createClient(gatewayAddress, clientBuilder);
  }
}
