package io.scalecube.services.benchmarks.gateway;

import io.scalecube.benchmarks.BenchmarkSettings;
import io.scalecube.benchmarks.BenchmarkState;
import io.scalecube.net.Address;
import io.scalecube.services.Microservices;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.gateway.ReferenceCountUtil;
import io.scalecube.services.transport.gw.client.GatewayClient;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

public abstract class AbstractBenchmarkState<T extends AbstractBenchmarkState<T>>
    extends BenchmarkState<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBenchmarkState.class);

  public static final ServiceMessage FIRST_REQUEST =
      ServiceMessage.builder().qualifier("/benchmarks/one").build();

  protected Function<Address, GatewayClient> clientBuilder;

  public AbstractBenchmarkState(
      BenchmarkSettings settings, Function<Address, GatewayClient> clientBuilder) {
    super(settings);
    this.clientBuilder = clientBuilder;
  }

  @Override
  protected void beforeAll() throws Exception {
    super.beforeAll();
    int workerCount = Runtime.getRuntime().availableProcessors();
  }

  public abstract Mono<GatewayClient> createClient();

  protected final Mono<GatewayClient> createClient(
      Microservices gateway,
      String gatewayName,
      Function<Address, GatewayClient> clientBuilder) {
    return Mono.defer(() -> createClient(gateway.gateway(gatewayName).address(), clientBuilder));
  }

  protected final Mono<GatewayClient> createClient(
      Address gatewayAddress, Function<Address, GatewayClient> clientBuilder) {
    return Mono.defer(
        () -> {
          GatewayClient client = clientBuilder.apply(gatewayAddress);
          return client
              .requestResponse(FIRST_REQUEST)
              .log("benchmark-client-first-request", Level.INFO, false, SignalType.ON_NEXT)
              .doOnNext(
                  response ->
                      Optional.ofNullable(response.data())
                          .ifPresent(ReferenceCountUtil::safestRelease))
              .then(Mono.just(client))
              .doOnNext(c -> LOGGER.info("benchmark-client: {}", c));
        });
  }
}
