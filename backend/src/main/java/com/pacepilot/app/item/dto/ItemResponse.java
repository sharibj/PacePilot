package com.pacepilot.app.item.dto;

import com.pacepilot.app.item.Item;
import java.time.Instant;

public record ItemResponse(Long id, String name, Instant createdAt) {
  public static ItemResponse from(Item item) {
    return new ItemResponse(item.getId(), item.getName(), item.getCreatedAt());
  }
}
