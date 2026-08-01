package com.pacepilot.app.unifier;

import java.util.List;

/**
 * Thrown when a raw telemetry event fails required-field validation. Carries the list of human
 * readable validation errors so the listener can log why the event was dead-lettered.
 */
public class InvalidTelemetryException extends RuntimeException {

  private final List<String> errors;

  public InvalidTelemetryException(List<String> errors) {
    super("Invalid telemetry event: " + String.join("; ", errors));
    this.errors = List.copyOf(errors);
  }

  public List<String> errors() {
    return errors;
  }
}
