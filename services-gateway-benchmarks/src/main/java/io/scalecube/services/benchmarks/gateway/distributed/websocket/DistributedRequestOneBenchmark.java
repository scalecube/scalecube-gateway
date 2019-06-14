package io.scalecube.services.benchmarks.gateway.distributed.websocket;

import io.scalecube.services.benchmarks.gateway.GwClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.distributed.DistributedBenchmarkState;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.websocket.WebsocketGwClient;

public class DistributedRequestOneBenchmark {

  /**
   * Main runner.
   *
   * @param args program arguments
   */
  public static void main(String[] args) {
    RequestOneScenario.runWith(
        args,
        benchmarkSettings ->
            new DistributedBenchmarkState(
                benchmarkSettings,
                "ws",
                (address, loopResources) ->
                    new WebsocketGwClient(
                        GwClientSettings.builder().address(address).loopResources(loopResources)
                            .build(), GwClientCodecs.WEBSOCKET_CLIENT_CODEC)));
  }
}
