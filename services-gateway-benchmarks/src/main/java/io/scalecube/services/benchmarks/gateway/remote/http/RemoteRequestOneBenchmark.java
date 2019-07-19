package io.scalecube.services.benchmarks.gateway.remote.http;

import static io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState.HTTP_PORT;

import io.scalecube.services.benchmarks.gateway.GatewayClientCodecs;
import io.scalecube.services.benchmarks.gateway.RequestOneScenario;
import io.scalecube.services.benchmarks.gateway.remote.RemoteBenchmarkState;
import io.scalecube.services.gateway.transport.GatewayClientSettings;
import io.scalecube.services.gateway.transport.http.HttpGatewayClient;

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
                    new HttpGatewayClient(
                        GatewayClientSettings.builder().address(address).build(),
                        GatewayClientCodecs.HTTP_CLIENT_CODEC)));
  }
}
