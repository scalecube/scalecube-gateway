package io.scalecube.services.benchmarks.gateway.remote.rsocket;

import static io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState.RS_PORT;

import io.scalecube.services.benchmarks.gateway.GwClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.rsocket.RSocketGwClient;

public class RemoteRequestOneBenchmark {

  /**
   * Main runner.
   *
   * @param args program arguments
   */
  public static void main(String[] args) {
    RequestOneScenario.runWith(
        args,
        benchmarkSettings ->
            new RemoteBenchmarkState(
                benchmarkSettings,
                RS_PORT,
                address ->
                    new RSocketGwClient(
                        GwClientSettings.builder().address(address)
                            .build(), GwClientCodecs.RSOCKET_CLIENT_CODEC)));
  }
}
