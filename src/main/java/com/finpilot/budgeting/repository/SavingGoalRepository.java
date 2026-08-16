package com.finpilot.budgeting.repository;

import com.finpilot.budgeting.domain.BudgetEnums;
import com.finpilot.budgeting.domain.SavingsGoal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.*;

public interface SavingGoalRepository extends JpaRepository<SavingsGoal,UUID> {

    List<SavingsGoal> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<SavingsGoal> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, BudgetEnums.SavingsGoalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SavingsGoal g where g.id=:id and g.userId=:userId")
    Optional<SavingsGoal> findLockedByIdAndUserId(UUID userId);
}
