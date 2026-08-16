package com.finpilot.budgeting.domain;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.time.Instant; import java.time.LocalDate; import java.util.UUID;
@Entity @Table(name="savings_contributions")
public class SavingsContribution {
 @Id @GeneratedValue private UUID id;
 @Column(name="savings_goal_id",nullable=false) private UUID savingsGoalId;
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
 private String note;
 @Column(name="contribution_date",nullable=false) private LocalDate contributionDate;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected SavingsContribution(){}
 public SavingsContribution(UUID goalId,UUID userId,BigDecimal amount,String note,LocalDate date){this.savingsGoalId=goalId;this.userId=userId;this.amount=amount;this.note=note;this.contributionDate=date;}
 @PrePersist void created(){createdAt=Instant.now();}
}
