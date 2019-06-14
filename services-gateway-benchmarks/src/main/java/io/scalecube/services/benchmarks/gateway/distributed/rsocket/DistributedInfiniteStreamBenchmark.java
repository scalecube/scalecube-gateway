package io.scalecube.services.benchmarks.gateway.distributed.rsocket;

import io.scalecube.services.benchmarks.gateway.GwClientCodecs;
import io.scalecube.services.benchmarks.gateway.InfiniteStreamScenario;
import io.scalecube.services.benchmarks.gateway.distributed.DistributedBenchmarkState;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.rsocket.RSocketGwClient;

public class DistributedInfiniteStreamBenchmark {

  /**
   * Main runner.
   *
   * @param args program arguments
   */
  public static void main(String[] args) {
    InfiniteStreamScenario.runWith(
        args,
        benchmarkSettings ->
            new DistributedBenchmarkState(
                benchmarkSettings,
                "rsws",
                (address, loopResources) ->
                    new RSocketGwClient(
                        GwClientSettings.builder().address(address).loopResources(loopResources)
                            .build(), GwClientCodecs.RSOCKET_CLIENT_CODEC)));
  }
}
