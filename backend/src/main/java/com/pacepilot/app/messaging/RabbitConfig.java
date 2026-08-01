package com.pacepilot.app.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the pacer RabbitMQ topology (topic exchange, dead-letter exchange, queues, and bindings)
 * and configures JSON (de)serialization for all pacer messages. Queues route rejected messages to
 * the dead-letter exchange so malformed events land on {@link PacerTopology#Q_DEADLETTER}.
 */
@Configuration
public class RabbitConfig {

  @Bean
  TopicExchange pacerExchange() {
    return new TopicExchange(PacerTopology.EXCHANGE, true, false);
  }

  @Bean
  TopicExchange pacerDlxExchange() {
    return new TopicExchange(PacerTopology.DLX_EXCHANGE, true, false);
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(PacerTopology.Q_DEADLETTER).build();
  }

  @Bean
  Queue rawQueue() {
    return durableWithDlx(PacerTopology.Q_TELEMETRY_RAW);
  }

  @Bean
  Queue canonicalQueue() {
    return durableWithDlx(PacerTopology.Q_TELEMETRY_CANONICAL);
  }

  @Bean
  Queue aggregatedQueue() {
    return durableWithDlx(PacerTopology.Q_TELEMETRY_AGGREGATED);
  }

  @Bean
  Queue cueTextQueue() {
    return durableWithDlx(PacerTopology.Q_CUE_TEXT);
  }

  private Queue durableWithDlx(String name) {
    return QueueBuilder.durable(name)
        .withArgument("x-dead-letter-exchange", PacerTopology.DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", PacerTopology.RK_DEADLETTER)
        .build();
  }

  @Bean
  Binding rawBinding() {
    return BindingBuilder.bind(rawQueue()).to(pacerExchange()).with(PacerTopology.RK_TELEMETRY_RAW);
  }

  @Bean
  Binding canonicalBinding() {
    return BindingBuilder.bind(canonicalQueue())
        .to(pacerExchange())
        .with(PacerTopology.RK_TELEMETRY_CANONICAL);
  }

  @Bean
  Binding aggregatedBinding() {
    return BindingBuilder.bind(aggregatedQueue())
        .to(pacerExchange())
        .with(PacerTopology.RK_TELEMETRY_AGGREGATED);
  }

  @Bean
  Binding cueTextBinding() {
    return BindingBuilder.bind(cueTextQueue()).to(pacerExchange()).with(PacerTopology.RK_CUE_TEXT);
  }

  @Bean
  Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue())
        .to(pacerDlxExchange())
        .with(PacerTopology.RK_DEADLETTER);
  }

  @Bean
  MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(jsonMessageConverter);
    template.setExchange(PacerTopology.EXCHANGE);
    return template;
  }
}
