package com.finpilot.importengine.api;


import com.finpilot.auth.utils.CurrentUser;
import com.finpilot.transaction.domain.ImportBatch;
import com.finpilot.transaction.repository.ImportBatchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;

import java.util.UUID;

@RestController
public class ImportHistoryController {
    private final ImportBatchRepository importBatchRepository;

    public ImportHistoryController(ImportBatchRepository importBatchRepository) {
        this.importBatchRepository = importBatchRepository;
    }


    @GetMapping("/api/v1/imports")
    public ResponseEntity<?> getMyImports(@RequestParam(required = false, value = "userId") UUID userIdParam){
        UUID userId = CurrentUser.optionalUserId().orElse(userIdParam);

        if(userId == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).
                    body(Map.of("error","No authenticated user and no userId provided"));
        }

        List<ImportBatch> importBatchList = importBatchRepository.
                findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(importBatchList);
    }
}
