package com.finpilot.importengine.dto;

import java.util.List;

// Wraps a list of transactions — matches BatchCategorizationRequest in the AI service.
public class CategorizationBatchRequest {

    private List<CategorizationRequestItem> transactions;

    public CategorizationBatchRequest() {
    }

    public CategorizationBatchRequest(List<CategorizationRequestItem> transactions) {
        this.transactions = transactions;
    }

    public List<CategorizationRequestItem> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<CategorizationRequestItem> transactions) {
        this.transactions = transactions;
    }
}