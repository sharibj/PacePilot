package com.pacepilot.app.chat;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pacepilot.app.chat.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean ChatService service;

  @Test
  void chatReturnsReply() throws Exception {
    given(service.reply("hi")).willReturn(new ChatResponse("hello there"));

    mockMvc
        .perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("hello there"));
  }

  @Test
  void blankMessageReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
