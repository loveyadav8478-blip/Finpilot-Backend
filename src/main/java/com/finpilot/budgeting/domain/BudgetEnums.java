package com.finpilot.budgeting.domain;

public final class BudgetEnums {

    private BudgetEnums() {
    }

    public enum BudgetAlertStatus {
        NORMAL, WARNING, EXCEEDED
    }

    public enum SavingsGoalStatus {
        IN_PROGRESS, COMPLETED, CANCELLED
    }
}
