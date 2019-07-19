package io.scalecube.services.benchmarks.gateway.distributed.websocket;

import io.scalecube.services.benchmarks.gateway.GatewayClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.distributed.DistributedBenchmarkState;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClient;

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
                address ->
                    new WebsocketGatewayClient(
                        GatewayClientSettings.builder().address(address).build(),
                        GatewayClientCodecs.WEBSOCKET_CLIENT_CODEC)));
  }
}
