package com.pacepilot.app.messaging;

/**
 * Single source of truth for RabbitMQ exchange, routing key, and queue names. Every pacer module
 * references these constants so the topology stays consistent with {@code docs/pacer-contracts.md}.
 */
public final class PacerTopology {

  private PacerTopology() {}

  public static final String EXCHANGE = "pacer.events";
  public static final String DLX_EXCHANGE = "pacer.events.dlx";

  // Routing keys
  public static final String RK_TELEMETRY_RAW = "telemetry.raw";
  public static final String RK_TELEMETRY_CANONICAL = "telemetry.canonical";
  public static final String RK_TELEMETRY_AGGREGATED = "telemetry.aggregated";
  public static final String RK_CUE_TEXT = "cue.text";
  public static final String RK_DEADLETTER = "deadletter";

  // Queues
  public static final String Q_TELEMETRY_RAW = "pacer.telemetry.raw.q";
  public static final String Q_TELEMETRY_CANONICAL = "pacer.telemetry.canonical.q";
  public static final String Q_TELEMETRY_AGGREGATED = "pacer.telemetry.aggregated.q";
  public static final String Q_CUE_TEXT = "pacer.cue.text.q";
  public static final String Q_DEADLETTER = "pacer.deadletter.q";
}
