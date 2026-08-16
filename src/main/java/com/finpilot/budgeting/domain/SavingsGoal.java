package com.finpilot.budgeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "savings_goal_status")
    private BudgetEnums.SavingsGoalStatus status = BudgetEnums.SavingsGoalStatus.IN_PROGRESS;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavingsGoal() {
    }

    public SavingsGoal(UUID userId, String title, String description, BigDecimal targetAmount,
                       String currency, LocalDate targetDate) {
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.targetAmount = targetAmount;
        this.currency = currency;
        this.targetDate = targetDate;
    }

    public void addContribution(BigDecimal amount) {
        if (status != BudgetEnums.SavingsGoalStatus.IN_PROGRESS) {
            throw new IllegalStateException("Contributions can only be added to an in-progress savings goal");
        }
        currentAmount = currentAmount.add(amount);
        if (currentAmount.compareTo(targetAmount) >= 0) {
            status = BudgetEnums.SavingsGoalStatus.COMPLETED;
        }
    }

    public void cancel() {
        if (status != BudgetEnums.SavingsGoalStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress savings goal can be cancelled");
        }
        status = BudgetEnums.SavingsGoalStatus.CANCELLED;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public BigDecimal getCurrentAmount() { return currentAmount; }
    public String getCurrency() { return currency; }
    public LocalDate getTargetDate() { return targetDate; }
    public BudgetEnums.SavingsGoalStatus getStatus() { return status; }
}
