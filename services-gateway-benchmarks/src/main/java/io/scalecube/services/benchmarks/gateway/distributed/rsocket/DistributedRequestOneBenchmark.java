package io.scalecube.services.benchmarks.gateway.distributed.rsocket;

import io.scalecube.services.benchmarks.gateway.GatewayClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.distributed.DistributedBenchmarkState;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.rsocket.RSocketGatewayClient;

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
                "rsws",
                address ->
                    new RSocketGatewayClient(
                        GatewayClientSettings.builder().address(address).build(),
                        GatewayClientCodecs.RSOCKET_CLIENT_CODEC)));
  }
}
