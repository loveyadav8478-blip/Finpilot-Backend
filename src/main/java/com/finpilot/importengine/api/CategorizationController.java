package com.finpilot.importengine.api;

import com.finpilot.auth.utils.CurrentUser;
import com.finpilot.importengine.service.CategorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Transitional state, same as CsvImportController: prefers the
 * authenticated user from the JWT if present, falls back to a manual
 * userId param otherwise.
 */
@RestController
public class CategorizationController {

    private final CategorizationService categorizationService;

    public CategorizationController(CategorizationService categorizationService) {
        this.categorizationService = categorizationService;
    }

    @PostMapping("/api/v1/categorize")
    public ResponseEntity<?> categorize(@RequestParam(value = "userId", required = false) UUID userIdParam) {
        UUID userId = CurrentUser.optionalUserId().orElse(userIdParam);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No authenticated user and no userId provided"));
        }

        int applied = categorizationService.categorizeUncategorized(userId);
        return ResponseEntity.ok(Map.of("categorized", applied));
    }
}