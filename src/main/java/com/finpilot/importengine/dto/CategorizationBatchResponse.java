package com.finpilot.importengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// Matches BatchCategorizationResponse in the AI service.
public class CategorizationBatchResponse {

    private List<CategorizationResultItem> results;
    private int total;
    @JsonProperty("low_confidence_count")
    private int lowConfidenceCount;

    public List<CategorizationResultItem> getResults() {
        return results;
    }

    public void setResults(List<CategorizationResultItem> results) {
        this.results = results;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getLowConfidenceCount() {
        return lowConfidenceCount;
    }

    public void setLowConfidenceCount(int lowConfidenceCount) {
        this.lowConfidenceCount = lowConfidenceCount;
    }
}