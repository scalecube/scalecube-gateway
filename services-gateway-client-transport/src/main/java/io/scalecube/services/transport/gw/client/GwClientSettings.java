package io.scalecube.services.transport.gw.client;

import io.scalecube.net.Address;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import io.scalecube.services.exceptions.ServiceClientErrorMapper;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.SslProvider;

public class GwClientSettings {

  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_CONTENT_TYPE = "application/json";

  private final String host;
  private final int port;
  private final String contentType;
  private final LoopResources loopResources;
  private final boolean followRedirect;
  private final SslProvider sslProvider;
  private final ServiceClientErrorMapper errorMapper;

  private GwClientSettings(Builder builder) {
    this.host = builder.host;
    this.port = builder.port;
    this.contentType = builder.contentType;
    this.loopResources = builder.loopResources;
    this.followRedirect = builder.followRedirect;
    this.sslProvider = builder.sslProvider;
    this.errorMapper = builder.errorMapper;
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }

  public String contentType() {
    return this.contentType;
  }

  public LoopResources loopResources() {
    return loopResources;
  }

  public boolean followRedirect() {
    return followRedirect;
  }

  public SslProvider sslProvider() {
    return sslProvider;
  }

  public ServiceClientErrorMapper errorMapper() {
    return errorMapper;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder from(GwClientSettings gwClientSettings) {
    return new Builder(gwClientSettings);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("GwClientSettings{");
    sb.append("host='").append(host).append('\'');
    sb.append(", port=").append(port);
    sb.append(", contentType='").append(contentType).append('\'');
    sb.append(", loopResources=").append(loopResources);
    sb.append(", followRedirect=").append(followRedirect);
    sb.append(", sslProvider=").append(sslProvider);
    sb.append('}');
    return sb.toString();
  }

  public static class Builder {

    private String host = DEFAULT_HOST;
    private int port;
    private String contentType = DEFAULT_CONTENT_TYPE;
    private LoopResources loopResources;
    private boolean followRedirect = true;
    private SslProvider sslProvider;
    private ServiceClientErrorMapper errorMapper = DefaultErrorMapper.INSTANCE;

    private Builder() {}

    private Builder(GwClientSettings originalSettings) {
      this.host = originalSettings.host;
      this.port = originalSettings.port;
      this.contentType = originalSettings.contentType;
      this.loopResources = originalSettings.loopResources;
      this.followRedirect = originalSettings.followRedirect;
      this.sslProvider = originalSettings.sslProvider;
      this.errorMapper = originalSettings.errorMapper;
    }

    public Builder host(String host) {
      this.host = host;
      return this;
    }

    public Builder port(int port) {
      this.port = port;
      return this;
    }

    public Builder address(Address address) {
      return host(address.host()).port(address.port());
    }

    public Builder contentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    /**
     * Specifies is auto-redirect enabled for HTTP 301/302 status codes. Enabled by default.
     *
     * @param followRedirect if <code>true</code> auto-redirect is enabled, otherwise disabled
     * @return builder
     */
    public Builder followRedirect(boolean followRedirect) {
      this.followRedirect = followRedirect;
      return this;
    }

    /**
     * Use default SSL client provider.
     *
     * @return builder
     */
    public Builder secure() {
      this.sslProvider = SslProvider.defaultClientProvider();
      return this;
    }

    /**
     * Use specified SSL provider.
     *
     * @param sslProvider SSL provider
     * @return builder
     */
    public Builder secure(SslProvider sslProvider) {
      this.sslProvider = sslProvider;
      return this;
    }

    public Builder errorMapper(ServiceClientErrorMapper errorMapper) {
      this.errorMapper = errorMapper;
      return this;
    }

    public GwClientSettings build() {
      return new GwClientSettings(this);
    }
  }
}
