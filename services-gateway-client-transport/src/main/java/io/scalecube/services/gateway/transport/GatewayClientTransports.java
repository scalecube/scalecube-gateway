package io.scalecube.services.gateway.transport;

import io.scalecube.services.gateway.transport.http.HttpGatewayClient;
import io.scalecube.services.gateway.transport.http.HttpGatewayClientCodec;
import io.scalecube.services.gateway.transport.rsocket.RSocketGatewayClient;
import io.scalecube.services.gateway.transport.rsocket.RSocketGatewayClientCodec;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClient;
import io.scalecube.services.gateway.transport.websocket.WebsocketGatewayClientCodec;
import io.scalecube.services.transport.api.ClientTransport;
import io.scalecube.services.transport.api.DataCodec;
import io.scalecube.services.transport.api.HeadersCodec;
import java.util.function.Function;

public class GatewayClientTransports {

  private static final String CONTENT_TYPE = "application/json";
  private static final HeadersCodec HEADERS_CODEC = HeadersCodec.getInstance(CONTENT_TYPE);

  public static final WebsocketGatewayClientCodec WEBSOCKET_CLIENT_CODEC =
      new WebsocketGatewayClientCodec();

  public static final RSocketGatewayClientCodec RSOCKET_CLIENT_CODEC =
      new RSocketGatewayClientCodec(HEADERS_CODEC, DataCodec.getInstance(CONTENT_TYPE));

  public static final HttpGatewayClientCodec HTTP_CLIENT_CODEC =
      new HttpGatewayClientCodec(DataCodec.getInstance(CONTENT_TYPE));

  private GatewayClientTransports() {
    // utils
  }

  /**
   * ClientTransport that is capable of communicating with Gateway over rSocket.
   *
   * @param cs client settings for gateway client transport
   * @return client transport
   */
  public static ClientTransport rsocketGatewayClientTransport(GatewayClientSettings cs) {
    final Function<GatewayClientSettings, GatewayClient> function =
        settings -> new RSocketGatewayClient(settings, RSOCKET_CLIENT_CODEC);
    return new GatewayClientTransport(function.apply(cs));
  }

  /**
   * ClientTransport that is capable of communicating with Gateway over websocket.
   *
   * @param cs client settings for gateway client transport
   * @return client transport
   */
  public static ClientTransport websocketGatewayClientTransport(GatewayClientSettings cs) {
    final Function<GatewayClientSettings, GatewayClient> function =
        settings -> new WebsocketGatewayClient(settings, WEBSOCKET_CLIENT_CODEC);
    return new GatewayClientTransport(function.apply(cs));
  }

  /**
   * ClientTransport that is capable of communicating with Gateway over http.
   *
   * @param cs client settings for gateway client transport
   * @return client transport
   */
  public static ClientTransport httpGatewayClientTransport(GatewayClientSettings cs) {
    final Function<GatewayClientSettings, GatewayClient> function =
        settings -> new HttpGatewayClient(settings, HTTP_CLIENT_CODEC);
    return new GatewayClientTransport(function.apply(cs));
  }
}
