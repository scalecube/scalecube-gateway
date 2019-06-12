package io.scalecube.services.transport.gw;

import io.netty.buffer.ByteBuf;
import io.rsocket.Payload;
import io.scalecube.services.transport.api.ClientTransport;
import io.scalecube.services.transport.api.DataCodec;
import io.scalecube.services.transport.api.HeadersCodec;
import io.scalecube.services.transport.api.ServerTransport;
import io.scalecube.services.transport.api.ServiceMessageCodec;
import io.scalecube.services.transport.api.ServiceTransport;
import io.scalecube.services.transport.api.TransportResources;
import io.scalecube.services.transport.gw.client.GwClientCodec;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.http.HttpGwClient;
import io.scalecube.services.transport.gw.client.http.HttpGwClientCodec;
import io.scalecube.services.transport.gw.client.rsocket.RSocketGwClient;
import io.scalecube.services.transport.gw.client.rsocket.RSocketGwClientCodec;
import io.scalecube.services.transport.gw.client.websocket.WebsocketGwClient;
import io.scalecube.services.transport.gw.client.websocket.WebsocketGwClientCodec;
import io.scalecube.services.transport.rsocket.DelegatedLoopResources;
import io.scalecube.services.transport.rsocket.RSocketServerTransport;
import io.scalecube.services.transport.rsocket.RSocketTransportResources;
import reactor.netty.resources.LoopResources;

public class GwServiceTransports {

  public static final GwServiceTransports INSTANCE = new GwServiceTransports();

  private static final String CONTENT_TYPE = "application/json";

  private static final HeadersCodec HEADERS_CODEC = HeadersCodec.getInstance(CONTENT_TYPE);
  private static final ServiceMessageCodec MESSAGE_CODEC = new ServiceMessageCodec(HEADERS_CODEC);
  private static final LoopResources LOOP_RESOURCES = LoopResources.create("gw-client-worker");

  private static final GwClientCodec<ByteBuf> WEBSOCKET_CLIENT_CODEC =
      new WebsocketGwClientCodec(DataCodec.getInstance(CONTENT_TYPE));
  private static final GwClientCodec<Payload> RSOCKET_CLIENT_CODEC =
      new RSocketGwClientCodec(HEADERS_CODEC, DataCodec.getInstance(CONTENT_TYPE));
  private static final GwClientCodec<ByteBuf> HTTP_CLIENT_CODEC =
      new HttpGwClientCodec(DataCodec.getInstance(CONTENT_TYPE));

  private GwServiceTransports() {
    // one instance;
  }

  /**
   * Service transport using Gateway over websocket as client transport and rSocket as server.
   * transport
   *
   * @param cs client settings for gateway client transport
   * @return service transport
   */
  public ServiceTransport websocketGwServiceTransport(GwClientSettings cs) {
    return new ServiceTransport() {
      @Override
      public ClientTransport clientTransport(TransportResources transportResources) {
        GwClientSettings actualClientSettings =
            updateSettingsWithResources((RSocketTransportResources) transportResources, cs);
        return new GwClientTransport(
            actualClientSettings,
            settings -> new WebsocketGwClient(settings, WEBSOCKET_CLIENT_CODEC));
      }

      @Override
      public ServerTransport serverTransport(TransportResources transportResources) {
        return rsocketServiceTransport((RSocketTransportResources) transportResources);
      }
    };
  }

  /**
   * Service transport using Gateway over http as client transport and rSocket as server transport.
   *
   * @param cs client settings for gateway client transport
   * @return service transport
   */
  public ServiceTransport httpGwServiceTransport(GwClientSettings cs) {
    return new ServiceTransport() {
      @Override
      public ClientTransport clientTransport(TransportResources transportResources) {
        GwClientSettings actualClientSettings =
            updateSettingsWithResources((RSocketTransportResources) transportResources, cs);
        return new GwClientTransport(
            actualClientSettings, settings -> new HttpGwClient(settings, HTTP_CLIENT_CODEC));
      }

      @Override
      public ServerTransport serverTransport(TransportResources transportResources) {
        return rsocketServiceTransport((RSocketTransportResources) transportResources);
      }
    };
  }

  /**
   * Service transport using Gateway over rsocket as client transport and rSocket as server.
   * transport
   *
   * @param cs client settings for gateway client transport
   * @return service transport
   */
  public ServiceTransport rsocketGwServiceTransport(GwClientSettings cs) {

    return new ServiceTransport() {
      @Override
      public ClientTransport clientTransport(TransportResources transportResources) {
        GwClientSettings actualClientSettings =
            updateSettingsWithResources((RSocketTransportResources) transportResources, cs);
        return new GwClientTransport(
            actualClientSettings, settings -> new RSocketGwClient(settings, RSOCKET_CLIENT_CODEC));
      }

      @Override
      public ServerTransport serverTransport(TransportResources transportResources) {
        return rsocketServiceTransport((RSocketTransportResources) transportResources);
      }
    };
  }

  private GwClientSettings updateSettingsWithResources(
      RSocketTransportResources transportResources, GwClientSettings cs) {
    LoopResources actualResources =
        transportResources
            .workerPool()
            .<LoopResources>map(DelegatedLoopResources::newClientLoopResources)
            .orElse(LOOP_RESOURCES);
    return GwClientSettings.from(cs).loopResources(actualResources).build();
  }

  private RSocketServerTransport rsocketServiceTransport(RSocketTransportResources resources) {
    return new RSocketServerTransport(
        MESSAGE_CODEC,
        resources
            .workerPool()
            .<LoopResources>map(DelegatedLoopResources::newServerLoopResources)
            .orElse(LOOP_RESOURCES));
  }
}
