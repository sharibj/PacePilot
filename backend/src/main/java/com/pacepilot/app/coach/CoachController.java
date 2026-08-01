package com.pacepilot.app.coach;

import com.pacepilot.app.coach.dto.CoachRequest;
import com.pacepilot.app.coach.dto.CoachResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coach chat endpoint. Relays a message to the n8n Planner agent and returns its reply. The {@code
 * sessionId} threads the conversation so the agent's Redis memory persists across turns.
 */
@RestController
@RequestMapping("/api/coach")
public class CoachController {

  private final N8nPlannerResolver planner;

  public CoachController(N8nPlannerResolver planner) {
    this.planner = planner;
  }

  @PostMapping
  public CoachResponse chat(@Valid @RequestBody CoachRequest request) {
    return new CoachResponse(planner.ask(request.sessionId(), request.message()));
  }
}
