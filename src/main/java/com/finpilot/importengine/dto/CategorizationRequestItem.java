package com.finpilot.importengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// One transaction to send to the AI service for categorization.
// Matches TransactionInput in the FastAPI service's schemas.py — just the
// fields the AI service actually needs to make a decision.
public class CategorizationRequestItem {

    @JsonProperty("transaction_id")
    private String transactionId;
    private String description;
    private double amount;

    public CategorizationRequestItem() {
    }

    public CategorizationRequestItem(String transactionId, String description, double amount) {
        this.transactionId = transactionId;
        this.description = description;
        this.amount = amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}