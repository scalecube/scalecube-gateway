package io.scalecube.services.benchmarks.gateway.remote.websocket;

import static io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState.WS_PORT;

import io.scalecube.services.benchmarks.gateway.GatewayClientCodecs;
import io.scalecube.services.benchmarks.gateway.InfiniteStreamScenario;
import io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClient;

public class RemoteInfiniteStreamBenchmark {

  /**
   * Main runner.
   *
   * @param args program arguments
   */
  public static void main(String[] args) {
    InfiniteStreamScenario.runWith(
        args,
        benchmarkSettings ->
            new RemoteBenchmarkState(
                benchmarkSettings,
                WS_PORT,
                address ->
                    new WebsocketGatewayClient(
                        GatewayClientSettings.builder().address(address).build(),
                        GatewayClientCodecs.WEBSOCKET_CLIENT_CODEC)));
  }
}
