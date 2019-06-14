package io.scalecube.services.benchmarks.gateway;

import io.netty.buffer.ByteBuf;
import io.rsocket.Payload;
import io.scalecube.services.transport.api.DataCodec;
import io.scalecube.services.transport.api.HeadersCodec;
import io.scalecube.services.transport.gw.client.GwClientCodec;
import io.scalecube.services.transport.gw.client.http.HttpGwClientCodec;
import io.scalecube.services.transport.gw.client.rsocket.RSocketGwClientCodec;
import io.scalecube.services.transport.gw.client.websocket.WebsocketGwClientCodec;

public class GwClientCodecs {

  public static final String CONTENT_TYPE = "application/json";

  public static final HeadersCodec HEADERS_CODEC = HeadersCodec.getInstance(CONTENT_TYPE);

  public static final GwClientCodec<ByteBuf> WEBSOCKET_CLIENT_CODEC =
      new WebsocketGwClientCodec(DataCodec.getInstance(CONTENT_TYPE));
  public static final GwClientCodec<Payload> RSOCKET_CLIENT_CODEC =
      new RSocketGwClientCodec(HEADERS_CODEC, DataCodec.getInstance(CONTENT_TYPE));
  public static final GwClientCodec<ByteBuf> HTTP_CLIENT_CODEC =
      new HttpGwClientCodec(DataCodec.getInstance(CONTENT_TYPE));

  private GwClientCodecs() {
    // one instance;
  }
}
