package io.scalecube.services.benchmarks.gateway;

import io.netty.buffer.ByteBuf;
import io.rsocket.Payload;
import io.scalecube.services.gateway.transport.GatewayClientCodec;
import io.scalecube.services.gateway.transport.http.HttpGatewayClientCodec;
import io.scalecube.services.gateway.transport.rsocket.RSocketGatewayClientCodec;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClientCodec;
import io.scalecube.services.transport.api.DataCodec;
import io.scalecube.services.transport.api.HeadersCodec;

public class GatewayClientCodecs {

  public static final String CONTENT_TYPE = "application/json";

  public static final HeadersCodec HEADERS_CODEC = HeadersCodec.getInstance(CONTENT_TYPE);

  public static final GatewayClientCodec<ByteBuf> WEBSOCKET_CLIENT_CODEC =
      new WebsocketGatewayClientCodec(DataCodec.getInstance(CONTENT_TYPE));
  public static final GatewayClientCodec<Payload> RSOCKET_CLIENT_CODEC =
      new RSocketGatewayClientCodec(HEADERS_CODEC, DataCodec.getInstance(CONTENT_TYPE));
  public static final GatewayClientCodec<ByteBuf> HTTP_CLIENT_CODEC =
      new HttpGatewayClientCodec(DataCodec.getInstance(CONTENT_TYPE));

  private GatewayClientCodecs() {
    // one instance;
  }
}
