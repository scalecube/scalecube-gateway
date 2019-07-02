package io.scalecube.services.benchmarks.gateway.remote.http;

import static io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState.HTTP_PORT;

import io.scalecube.services.benchmarks.gateway.GwClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.http.HttpGwClient;

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
                HTTP_PORT,
                address ->
                    new HttpGwClient(
                        GwClientSettings.builder().address(address)
                            .build(), GwClientCodecs.HTTP_CLIENT_CODEC)));
  }
}
