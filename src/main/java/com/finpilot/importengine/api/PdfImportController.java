package com.finpilot.importengine.api;

import com.finpilot.auth.utils.CurrentUser;
import com.finpilot.importengine.dto.ImportResultResponse;
import com.finpilot.importengine.exceptions.InvalidPdfPasswordException;
//import com.finpilot.importengine.service.DuplicateImportException;
import com.finpilot.importengine.service.*;
import com.finpilot.importengine.service.PdfImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
public class PdfImportController {

    private static final long MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024; // 15 MB

    private final PdfImportService pdfImportService;

    public PdfImportController(PdfImportService pdfImportService) {
        this.pdfImportService = pdfImportService;
    }

    @PostMapping(value = "/pdf", consumes = "multipart/form-data")
    public ResponseEntity<?> importPdf(
            @RequestParam(value = "userId", required = false) UUID userIdParam,
            @RequestParam(value = "accountId", required = false) UUID accountId,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam("file") MultipartFile file) {

        UUID userId = CurrentUser.optionalUserId().orElse(userIdParam);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("No authenticated user and no userId provided"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("Uploaded PDF file is empty"));
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(errorBody("File exceeds the 15MB limit for PDF imports"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(errorBody("Only .pdf files are accepted by this endpoint"));
        }

        try {
            ImportResultResponse result = pdfImportService.importPdf(userId, accountId, filename, file.getBytes(), password);
            return ResponseEntity.ok(result);
        } catch (InvalidPdfPasswordException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(e.getMessage()));
        } catch (DuplicateImportException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("Failed to read uploaded PDF file"));
        }
    }

    private static Map<String, String> errorBody(String message) {
        return Map.of("error", message);
    }
}
