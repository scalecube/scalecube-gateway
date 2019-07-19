package io.scalecube.services.benchmarks.gateway.standalone.http;

import io.scalecube.services.benchmarks.gateway.GatewayClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.standalone.StandaloneBenchmarkState;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.http.HttpGatewayClient;

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
                address ->
                    new HttpGatewayClient(
                        GatewayClientSettings.builder().address(address).build(),
                        GatewayClientCodecs.HTTP_CLIENT_CODEC)));
  }
}
