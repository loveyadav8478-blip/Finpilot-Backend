package com.finpilot.budgeting.repository;
import com.finpilot.budgeting.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

 Optional<Budget> findByUserIdAndCategory_IdAndPeriodMonth(UUID userId, UUID categoryId, String periodMonth);
 Optional<Budget> findByUserIdAndCategoryIsNullAndPeriodMonth(UUID userId, String periodMonth);
 List<Budget> findByUserIdAndPeriodMonthOrderByCreatedAtAsc(UUID userId, String periodMonth);
 Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
}
