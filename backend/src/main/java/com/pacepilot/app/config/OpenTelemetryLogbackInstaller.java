package com.pacepilot.app.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Wires application logs to OTLP.
 *
 * <p>Spring Boot autoconfigures OTLP metrics and traces, but not the logs signal, so we build a
 * minimal {@link SdkLoggerProvider} with an OTLP log exporter and install it into the Logback
 * appender declared in {@code logback-spring.xml}. Endpoint and service name come from the same env
 * contract as the rest of the app.
 */
@Component
class OpenTelemetryLogbackInstaller implements ApplicationListener<ApplicationReadyEvent> {

  private final String logsEndpoint;
  private final String serviceName;

  OpenTelemetryLogbackInstaller(
      @Value("${OTEL_LOGS_URL:http://localhost:4318/v1/logs}") String logsEndpoint,
      @Value("${spring.application.name:app}") String serviceName) {
    this.logsEndpoint = logsEndpoint;
    this.serviceName = serviceName;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    Resource resource =
        Resource.getDefault()
            .merge(
                Resource.create(
                    io.opentelemetry.api.common.Attributes.of(
                        ServiceAttributes.SERVICE_NAME, serviceName)));

    SdkLoggerProvider loggerProvider =
        SdkLoggerProvider.builder()
            .setResource(resource)
            .addLogRecordProcessor(
                BatchLogRecordProcessor.builder(
                        OtlpHttpLogRecordExporter.builder().setEndpoint(logsEndpoint).build())
                    .build())
            .build();

    OpenTelemetry openTelemetry =
        OpenTelemetrySdk.builder().setLoggerProvider(loggerProvider).build();

    OpenTelemetryAppender.install(openTelemetry);
  }
}
