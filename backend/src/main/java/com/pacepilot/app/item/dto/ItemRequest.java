package com.pacepilot.app.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ItemRequest(@NotBlank @Size(max = 255) String name) {}
