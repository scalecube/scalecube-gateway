package io.scalecube.services.gateway.ws;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.api.ServiceMessage.Builder;
import io.scalecube.services.exceptions.DefaultErrorMapper;
import java.util.Optional;

final class GatewayMessages {

  static final String QUALIFIER_FIELD = "q";
  static final String STREAM_ID_FIELD = "sid";
  static final String DATA_FIELD = "d";
  static final String SIGNAL_FIELD = "sig";
  static final String INACTIVITY_FIELD = "i";
  static final String RATE_LIMIT_FIELD = "rlimit";

  private GatewayMessages() {
    // Do not instantiate
  }

  static ServiceMessage newCancelMessage(long sid) {
    Builder builder = ServiceMessage.builder();
    setSid(builder, sid);
    setSignal(builder, Signal.CANCEL);
    return builder.build();
  }

  static ServiceMessage newErrorMessage(ServiceMessage message, Throwable th) {
    ServiceMessage.Builder builder =
        ServiceMessage.from(DefaultErrorMapper.INSTANCE.toMessage(message.qualifier(), th));
    Optional.ofNullable(GatewayMessages.getSidHeader(message))
        .ifPresent(s -> GatewayMessages.setSid(builder, s));
    GatewayMessages.setSignal(builder, Signal.ERROR);
    return builder.build();
  }

  static ServiceMessage newCompleteMessage(ServiceMessage message) {
    ServiceMessage.Builder builder = ServiceMessage.builder();
    Optional.ofNullable(GatewayMessages.getSidHeader(message))
        .ifPresent(s -> GatewayMessages.setSid(builder, s));
    GatewayMessages.setSignal(builder, Signal.COMPLETE);
    return builder.build();
  }

  static ServiceMessage newResponseMessage(
      long sid, ServiceMessage message, boolean isErrorResponse) {
    ServiceMessage.Builder builder = ServiceMessage.from(message);
    GatewayMessages.setSid(builder, sid);
    if (isErrorResponse) {
      GatewayMessages.setSignal(builder, Signal.ERROR);
    }
    return builder.build();
  }

  static ServiceMessage validateSid(ServiceMessage message) {
    if (getSidHeader(message) == null) {
      throw WebsocketContextException.badRequest("sid is missing", message);
    } else {
      return message;
    }
  }

  static ServiceMessage validateSidOnSession(
      WebsocketGatewaySession session, ServiceMessage message) {
    long sid = getSid(message);
    if (session.containsSid(sid)) {
      throw WebsocketContextException.badRequest("sid=" + sid + " is already registered", message);
    } else {
      return message;
    }
  }

  static ServiceMessage validateQualifier(ServiceMessage message) {
    if (message.qualifier() == null) {
      throw WebsocketContextException.badRequest("qualifier is missing", message);
    }
    return message;
  }

  static long getSid(ServiceMessage message) {
    return Long.parseLong(getSidHeader(message));
  }

  static String getSidHeader(ServiceMessage message) {
    return message.header(STREAM_ID_FIELD);
  }

  static ServiceMessage.Builder setSid(ServiceMessage.Builder builder, long sid) {
    return builder.header(STREAM_ID_FIELD, sid);
  }

  static ServiceMessage.Builder setSid(ServiceMessage.Builder builder, String sid) {
    return builder.header(STREAM_ID_FIELD, sid);
  }

  static Signal getSignal(ServiceMessage message) {
    String header = message.header(SIGNAL_FIELD);
    return header != null ? Signal.from(Integer.parseInt(header)) : null;
  }

  static ServiceMessage.Builder setSignal(ServiceMessage.Builder builder, Signal signal) {
    return builder.header(SIGNAL_FIELD, signal.code());
  }
}
