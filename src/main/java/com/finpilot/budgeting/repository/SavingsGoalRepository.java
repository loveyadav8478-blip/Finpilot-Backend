package com.finpilot.budgeting.repository;
import com.finpilot.budgeting.domain.*; import org.springframework.data.jpa.repository.*; import jakarta.persistence.LockModeType; import java.util.*;
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal,UUID>{

    List<SavingsGoal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SavingsGoal> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId,BudgetEnums.SavingsGoalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select g from SavingsGoal g where g.id=:id and g.userId=:userId")
    Optional<SavingsGoal> findLockedByIdAndUserId(UUID id,UUID userId);
}
