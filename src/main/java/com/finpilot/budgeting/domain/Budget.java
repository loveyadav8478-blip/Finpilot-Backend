package com.finpilot.budgeting.domain;

import com.finpilot.transaction.domain.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "monthly_limit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyLimitAmount;

    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    @Column(name = "alert_threshold_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal alertThresholdPercentage;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {
    }

    public Budget(UUID userId, Category category, BigDecimal monthlyLimitAmount,
                  String periodMonth, BigDecimal alertThresholdPercentage, String currency) {
        this.userId = userId;
        this.category = category;
        this.monthlyLimitAmount = monthlyLimitAmount;
        this.periodMonth = periodMonth;
        this.alertThresholdPercentage = alertThresholdPercentage;
        this.currency = currency;
    }

    public void update(BigDecimal monthlyLimitAmount, BigDecimal alertThresholdPercentage, String currency) {
        this.monthlyLimitAmount = monthlyLimitAmount;
        this.alertThresholdPercentage = alertThresholdPercentage;
        this.currency = currency;
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
    public Category getCategory() { return category; }
    public BigDecimal getMonthlyLimitAmount() { return monthlyLimitAmount; }
    public String getPeriodMonth() { return periodMonth; }
    public BigDecimal getAlertThresholdPercentage() { return alertThresholdPercentage; }
    public String getCurrency() { return currency; }
}
