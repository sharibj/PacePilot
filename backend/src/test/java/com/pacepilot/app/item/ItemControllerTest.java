package com.pacepilot.app.item;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pacepilot.app.item.dto.ItemRequest;
import com.pacepilot.app.item.dto.ItemResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ItemController.class)
class ItemControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean ItemService service;

  @Test
  void createReturns201() throws Exception {
    given(service.create(new ItemRequest("hello")))
        .willReturn(new ItemResponse(1L, "hello", Instant.now()));

    mockMvc
        .perform(
            post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"hello\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("hello"));
  }

  @Test
  void blankNameReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void deleteReturns204() throws Exception {
    mockMvc.perform(delete("/api/items/1")).andExpect(status().isNoContent());
    verify(service).delete(1L);
  }
}
