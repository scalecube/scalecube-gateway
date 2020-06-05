package io.scalecube.services.gateway.ws;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.exceptions.DefaultErrorMapper;

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

  static ServiceMessage newCancelMessage(long sid, String qualifier) {
    return ServiceMessage.builder()
        .qualifier(qualifier)
        .header(STREAM_ID_FIELD, sid)
        .header(SIGNAL_FIELD, Signal.CANCEL.code())
        .build();
  }

  static ServiceMessage newErrorMessage(ServiceMessage message, Throwable th) {
    ServiceMessage.Builder builder =
        ServiceMessage.from(DefaultErrorMapper.INSTANCE.toMessage(message.qualifier(), th))
            .header(SIGNAL_FIELD, Signal.ERROR.code());
    String sid = message.header(STREAM_ID_FIELD);
    if (sid != null) {
      builder.header(STREAM_ID_FIELD, sid);
    }
    return builder.build();
  }

  static ServiceMessage newCompleteMessage(long sid, String qualifier) {
    return ServiceMessage.builder()
        .qualifier(qualifier)
        .header(STREAM_ID_FIELD, sid)
        .header(SIGNAL_FIELD, Signal.COMPLETE.code())
        .build();
  }

  static ServiceMessage newResponseMessage(
      long sid, ServiceMessage message, boolean isErrorResponse) {
    if (isErrorResponse) {
      return ServiceMessage.from(message)
          .header(STREAM_ID_FIELD, sid)
          .header(SIGNAL_FIELD, Signal.ERROR.code())
          .build();
    }
    return ServiceMessage.from(message).header(STREAM_ID_FIELD, sid).build();
  }

  static ServiceMessage validateSid(ServiceMessage message) {
    if (message.header(STREAM_ID_FIELD) == null) {
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
    return Long.parseLong(message.header(STREAM_ID_FIELD));
  }

  static Signal getSignal(ServiceMessage message) {
    String header = message.header(SIGNAL_FIELD);
    return header != null ? Signal.from(Integer.parseInt(header)) : null;
  }
}
