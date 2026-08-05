package com.finpilot.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Basic liveness endpoint — used by Docker healthchecks and load balancers.
 * Deliberately does NOT touch the database; a DB-dependent readiness probe
 * belongs in Spring Actuator (added in the DevOps phase), kept separate so
 * a slow/degraded DB doesn't flip this into "unhealthy" and trigger
 * unnecessary container restarts.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", Instant.now());
    }

    public record HealthResponse(String status, Instant timestamp) {

    }
}
