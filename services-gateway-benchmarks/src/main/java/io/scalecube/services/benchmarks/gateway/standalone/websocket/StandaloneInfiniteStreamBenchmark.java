package io.scalecube.services.benchmarks.gateway.standalone.websocket;

import io.scalecube.services.Microservices;
import io.scalecube.services.benchmarks.gateway.InfiniteStreamScenario;
import io.scalecube.services.benchmarks.gateway.standalone.StandaloneBenchmarkState;
import io.scalecube.services.transport.gw.GwTransportBootstraps;
import io.scalecube.services.transport.gw.client.GwClientSettings;

public class StandaloneInfiniteStreamBenchmark {

  /**
   * Main runner.
   *
   * @param args program arguments
   */
  public static void main(String[] args) {
    InfiniteStreamScenario.runWith(
        args,
        benchmarkSettings ->
            new StandaloneBenchmarkState(
                benchmarkSettings,
                "ws",
                (address, loopResources) ->{
                  client = Microservices.builder().transport(op -> GwTransportBootstraps.websocketGwTransport(
                      GwClientSettings.builder().loopResources(loopResources).build(), op)).sta;
                  return
                  Client.websocket(
                      ClientSettings.builder()
                          .address(address)
                          .loopResources(loopResources)
                          .build())));
                }

  }
}
