package io.scalecube.services.transport.gw;

import io.netty.buffer.ByteBuf;
import io.rsocket.Payload;
import io.scalecube.services.transport.api.ClientTransport;
import io.scalecube.services.transport.api.DataCodec;
import io.scalecube.services.transport.api.HeadersCodec;
import io.scalecube.services.transport.gw.client.GwClientCodec;
import io.scalecube.services.transport.gw.client.GwClientSettings;
import io.scalecube.services.transport.gw.client.http.HttpGwClient;
import io.scalecube.services.transport.gw.client.http.HttpGwClientCodec;
import io.scalecube.services.transport.gw.client.rsocket.RSocketGwClient;
import io.scalecube.services.transport.gw.client.rsocket.RSocketGwClientCodec;
import io.scalecube.services.transport.gw.client.websocket.WebsocketGwClient;
import io.scalecube.services.transport.gw.client.websocket.WebsocketGwClientCodec;

public class GwClientTransports {

  private static final String CONTENT_TYPE = "application/json";
  private static final HeadersCodec HEADERS_CODEC = HeadersCodec.getInstance(CONTENT_TYPE);

  private static final GwClientCodec<ByteBuf> WEBSOCKET_CLIENT_CODEC =
      new WebsocketGwClientCodec(DataCodec.getInstance(CONTENT_TYPE));
  private static final GwClientCodec<Payload> RSOCKET_CLIENT_CODEC =
      new RSocketGwClientCodec(HEADERS_CODEC, DataCodec.getInstance(CONTENT_TYPE));
  private static final GwClientCodec<ByteBuf> HTTP_CLIENT_CODEC =
      new HttpGwClientCodec(DataCodec.getInstance(CONTENT_TYPE));

  private GwClientTransports() {
    // utils
  }

  /**
   * ClientTransport that is capable of communicating with Gateway over rSocket.
   *
   * @param cs client settings for gateway client transport
   * @return client transport
   */
  public static ClientTransport rsocketGwClientTransport(GwClientSettings cs) {
    return new GwClientTransport(
        cs, settings -> new RSocketGwClient(settings, RSOCKET_CLIENT_CODEC));
  }

  /**
   * ClientTransport that is capable of communicating with Gateway over websocket.
   *
   * @param cs client settings for gateway client transport
   * @return client transport
   */
  public static ClientTransport websocketGwClientTransport(GwClientSettings cs) {
    return new GwClientTransport(
        cs, settings -> new WebsocketGwClient(settings, WEBSOCKET_CLIENT_CODEC));
  }

  /**
   * ClientTransport that is capable of communicating with Gateway over http.
   *
   * @param cs client settings for gateway client transport
   * @return client transport
   */
  public static ClientTransport httpGwClientTransport(GwClientSettings cs) {
    return new GwClientTransport(cs, settings -> new HttpGwClient(settings, HTTP_CLIENT_CODEC));
  }
}
