package com.finpilot.importengine.api;

import com.finpilot.auth.utils.CurrentUser;
import com.finpilot.importengine.dto.ImportResultResponse;
import com.finpilot.importengine.service.CsvImportService;
import com.finpilot.importengine.service.DuplicateImportException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * CSV import endpoint.
 *
 * Transitional state: prefers the authenticated user (from the JWT, via
 * CurrentUser) if a valid token is present. Falls back to the manual
 * userId request param if not logged in — kept for backward compatibility
 * while auth is still being rolled out across the app. Once every caller
 * reliably sends a token, drop the param entirely and call
 * CurrentUser.requireUserId() instead, same as the fully-wired version.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class CsvImportController {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(value = "/csv", consumes = "multipart/form-data")
    public ResponseEntity<?> importCsv(
            @RequestParam(value = "userId", required = false) UUID userIdParam,
            @RequestParam(value = "accountId", required = false) UUID accountId,
            @RequestParam("file") MultipartFile file) {

        UUID userId = CurrentUser.optionalUserId().orElse(userIdParam);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("No authenticated user and no userId provided"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("Uploaded file is empty"));
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(errorBody("File exceeds the 10MB limit for CSV imports"));
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(errorBody("Only .csv files are accepted by this endpoint"));
        }

        try {
            ImportResultResponse result = csvImportService.importCsv(userId, accountId, filename, file.getBytes());
            return ResponseEntity.ok(result);
        } catch (DuplicateImportException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Failed to read uploaded file"));
        }
    }

    private static java.util.Map<String, String> errorBody(String message) {
        return java.util.Map.of("error", message);
    }
}