package io.scalecube.services.transport.gw.client.rsocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.MessageCodecException;
import io.scalecube.services.transport.api.DataCodec;
import io.scalecube.services.transport.api.HeadersCodec;
import io.scalecube.services.transport.api.ReferenceCountUtil;
import io.scalecube.services.transport.gw.client.GwClientCodec;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RSocketGwClientCodec implements GwClientCodec<Payload> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RSocketGwClientCodec.class);

  private final HeadersCodec headersCodec;
  private final DataCodec dataCodec;

  /**
   * Constructor for codec which encode/decode client message to/from rsocket payload.
   *
   * @param headersCodec headers message codec.
   * @param dataCodec data message codec.
   */
  public RSocketGwClientCodec(HeadersCodec headersCodec, DataCodec dataCodec) {
    this.headersCodec = headersCodec;
    this.dataCodec = dataCodec;
  }

  @Override
  public DataCodec getDataCodec() {
    return dataCodec;
  }

  @Override
  public Payload encode(ServiceMessage message) {
    return encodeAndTransform(message, ByteBufPayload::create);
  }

  @Override
  public ServiceMessage decode(Payload encodedMessage) {
    return decode(encodedMessage.sliceData(), encodedMessage.sliceMetadata());
  }

  /**
   * Decoder function. Keeps data buffer untouched.
   *
   * @param dataBuffer data buffer.
   * @param headersBuffer headers buffer.
   * @return client message object.
   * @throws MessageCodecException in case if decode fails.
   */
  private ServiceMessage decode(ByteBuf dataBuffer, ByteBuf headersBuffer)
      throws MessageCodecException {
    ServiceMessage.Builder builder = ServiceMessage.builder();

    if (dataBuffer.isReadable()) {
      builder.data(dataBuffer);
    }

    if (headersBuffer.isReadable()) {
      try (ByteBufInputStream stream = new ByteBufInputStream(headersBuffer, true)) {
        builder.headers(headersCodec.decode(stream));
      } catch (Throwable ex) {
        ReferenceCountUtil.safestRelease(dataBuffer); // release data as well
        throw new MessageCodecException("Failed to decode message headers", ex);
      }
    }

    return builder.build();
  }

  /**
   * Encoder function.
   *
   * @param message client message.
   * @param transformer bi function transformer from two headers and data buffers to client
   *     specified object of type T
   * @param <T> client specified type which could be constructed out of headers and data bufs.
   * @return T object
   * @throws MessageCodecException in case if encoding fails
   */
  private <T> T encodeAndTransform(
      ServiceMessage message, BiFunction<ByteBuf, ByteBuf, T> transformer)
      throws MessageCodecException {
    ByteBuf dataBuffer = Unpooled.EMPTY_BUFFER;
    ByteBuf headersBuffer = Unpooled.EMPTY_BUFFER;

    if (message.hasData(ByteBuf.class)) {
      dataBuffer = message.data();
    } else if (message.hasData()) {
      dataBuffer = ByteBufAllocator.DEFAULT.buffer();
      try {
        dataCodec.encode(new ByteBufOutputStream(dataBuffer), message.data());
      } catch (Throwable ex) {
        ReferenceCountUtil.safestRelease(dataBuffer);
        LOGGER.error("Failed to encode data on: {}, cause: {}", message, ex);
        throw new MessageCodecException(
            "Failed to encode data on message q=" + message.qualifier(), ex);
      }
    }

    if (!message.headers().isEmpty()) {
      headersBuffer = ByteBufAllocator.DEFAULT.buffer();
      try {
        headersCodec.encode(new ByteBufOutputStream(headersBuffer), message.headers());
      } catch (Throwable ex) {
        ReferenceCountUtil.safestRelease(headersBuffer);
        ReferenceCountUtil.safestRelease(dataBuffer); // release data as well
        LOGGER.error("Failed to encode headers on: {}, cause: {}", message, ex);
        throw new MessageCodecException(
            "Failed to encode headers on message q=" + message.qualifier(), ex);
      }
    }

    return transformer.apply(dataBuffer, headersBuffer);
  }
}
