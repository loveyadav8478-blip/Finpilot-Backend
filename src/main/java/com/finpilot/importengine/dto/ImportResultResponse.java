package com.finpilot.importengine.dto;

//import com.finpilot.importengine.csv.RowValidationError;

import com.finpilot.importengine.csv.RowValidationError;

import java.util.List;
import java.util.UUID;

public record ImportResultResponse(
        UUID importBatchId,
        String status,
        int totalRowsDetected,
        int rowsImported,
        int rowsSkippedDuplicate,
        int rowsFailed,
        String errorSummary, List<RowValidationError> errors
) {
}
