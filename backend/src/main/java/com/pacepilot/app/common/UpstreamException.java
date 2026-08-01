package com.pacepilot.app.common;

public class UpstreamException extends RuntimeException {
  public UpstreamException(String message, Throwable cause) {
    super(message, cause);
  }
}
