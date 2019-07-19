package io.scalecube.services.benchmarks.gateway.remote.websocket;

import static io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState.WS_PORT;

import io.scalecube.services.benchmarks.gateway.GwClientCodecs;
import io.scalecube.services.benchmarks.gateway.InfiniteStreamScenario;
import io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.websocket.WebsocketGwClient;

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
                    new WebsocketGwClient(
                        GwClientSettings.builder().address(address).build(),
                        GwClientCodecs.WEBSOCKET_CLIENT_CODEC)));
  }
}
