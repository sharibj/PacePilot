package com.pacepilot.app.coach.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A chat turn from the frontend Coach tab. {@code sessionId} keys the n8n Planner's Redis chat
 * memory so a conversation's history persists across turns.
 */
public record CoachRequest(@NotBlank String message, @NotBlank String sessionId) {}
