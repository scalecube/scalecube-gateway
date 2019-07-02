package io.scalecube.services.benchmarks.gateway.standalone.rsocket;

import io.scalecube.services.benchmarks.gateway.GwClientCodecs;
import io.scalecube.services.benchmarks.gateway.InfiniteStreamScenario;
import io.scalecube.services.benchmarks.gateway.standalone.StandaloneBenchmarkState;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.rsocket.RSocketGwClient;

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
                    new RSocketGwClient(
                        GwClientSettings.builder().address(address)
                            .build(), GwClientCodecs.RSOCKET_CLIENT_CODEC)));
  }
}
