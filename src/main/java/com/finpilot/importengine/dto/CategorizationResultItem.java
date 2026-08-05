package com.finpilot.importengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// What comes back for one transaction. Matches CategorizationResult in the
// FastAPI service's schemas.py — category, confidence, and where the
// answer came from (rule vs ML) so we can decide whether to trust it.
public class CategorizationResultItem {

    @JsonProperty("transaction_id")
    private String transactionId;
    private String category;
    private double confidence;
    private String source; // "RULE", "ML_MODEL", or "FALLBACK"

    public CategorizationResultItem() {
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}