package com.pacepilot.app.chat;

import com.pacepilot.app.chat.dto.ChatResponse;
import com.pacepilot.app.common.UpstreamException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

  private final ChatClient chatClient;
  private final MeterRegistry meters;
  private final String model;

  public ChatService(
      ChatClient.Builder chatClientBuilder,
      MeterRegistry meters,
      @Value("${spring.ai.openai.chat.options.model:unknown}") String model) {
    this.chatClient = chatClientBuilder.build();
    this.meters = meters;
    this.model = model;
  }

  public ChatResponse reply(String message) {
    // Custom metric example: count requests + time them, tagged by model.
    meters.counter("chat.requests", "model", model).increment();
    Timer.Sample sample = Timer.start(meters);
    try {
      org.springframework.ai.chat.model.ChatResponse aiResponse =
          chatClient.prompt().user(message).call().chatResponse();

      recordTokenUsage(aiResponse);

      String content =
          aiResponse != null && aiResponse.getResult() != null
              ? aiResponse.getResult().getOutput().getText()
              : "";
      return new ChatResponse(content);
    } catch (RuntimeException ex) {
      throw new UpstreamException("LLM request failed: " + ex.getMessage(), ex);
    } finally {
      sample.stop(meters.timer("chat.latency", "model", model));
    }
  }

  private void recordTokenUsage(org.springframework.ai.chat.model.ChatResponse aiResponse) {
    if (aiResponse == null || aiResponse.getMetadata() == null) {
      return;
    }
    var usage = aiResponse.getMetadata().getUsage();
    if (usage == null) {
      return;
    }
    // Token-usage metrics: the flagship custom metric for an LLM app.
    increment("llm.tokens.prompt", usage.getPromptTokens());
    increment("llm.tokens.completion", usage.getCompletionTokens());
    increment("llm.tokens.total", usage.getTotalTokens());
  }

  private void increment(String name, Integer count) {
    if (count != null && count > 0) {
      meters.counter(name, "model", model).increment(count);
    }
  }
}
