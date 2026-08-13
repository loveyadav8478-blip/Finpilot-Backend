package com.finpilot.transaction.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request payload for user category manual corrections.
 */
public record CategoryCorrectionRequest(
        @NotNull(message = "categoryId must not be null")
        UUID categoryId
) {
}
