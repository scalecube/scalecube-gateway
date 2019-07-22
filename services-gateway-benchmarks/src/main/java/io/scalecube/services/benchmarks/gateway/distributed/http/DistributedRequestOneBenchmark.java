package io.scalecube.services.benchmarks.gateway.distributed.http;

import io.scalecube.services.benchmarks.gateway.GatewayClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.distributed.DistributedBenchmarkState;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.http.HttpGatewayClient;

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
                "http",
                address ->
                    new HttpGatewayClient(
                        GatewayClientSettings.builder().address(address).build(),
                        GatewayClientCodecs.HTTP_CLIENT_CODEC)));
  }
}
