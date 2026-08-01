package com.pacepilot.app.chat;

import com.pacepilot.app.chat.dto.ChatRequest;
import com.pacepilot.app.chat.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private final ChatService service;

  public ChatController(ChatService service) {
    this.service = service;
  }

  @PostMapping
  public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    return service.reply(request.message());
  }
}
