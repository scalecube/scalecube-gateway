package io.scalecube.services.benchmarks.gateway.standalone.http;

import io.scalecube.services.benchmarks.gateway.GwClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.standalone.StandaloneBenchmarkState;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.http.HttpGwClient;

public class StandaloneRequestOneBenchmark {

  /**
   * Main runner.
   *
   * @param args program arguments
   */
  public static void main(String[] args) {
    RequestOneScenario.runWith(
        args,
        benchmarkSettings ->
            new StandaloneBenchmarkState(
                benchmarkSettings,
                "http",
                (address, loopResources) ->
                    new HttpGwClient(
                        GwClientSettings.builder().address(address).loopResources(loopResources)
                            .build(), GwClientCodecs.HTTP_CLIENT_CODEC)));
  }
}
