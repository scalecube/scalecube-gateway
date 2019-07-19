package io.scalecube.services.benchmarks.gateway.standalone.rsocket;

import io.scalecube.services.benchmarks.gateway.GatewayClientCodecs;
import io.scalecube.services.benchmarks.gateway.InfiniteStreamScenario;
import io.scalecube.services.benchmarks.gateway.standalone.StandaloneBenchmarkState;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.rsocket.RSocketGatewayClient;

public class StandaloneInfiniteStreamBenchmark {

  /**
   * Main runner.
   *
   * @param args program arguments
   */
  public static void main(String[] args) {
    InfiniteStreamScenario.runWith(
        args,
        benchmarkSettings ->
            new StandaloneBenchmarkState(
                benchmarkSettings,
                "rsws",
                address ->
                    new RSocketGatewayClient(
                        GatewayClientSettings.builder().address(address).build(),
                        GatewayClientCodecs.RSOCKET_CLIENT_CODEC)));
  }
}
