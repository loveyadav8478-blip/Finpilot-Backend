package com.finpilot.budgeting;

import com.finpilot.budgeting.domain.SavingsContribution;
import com.finpilot.budgeting.domain.SavingsGoal;
import com.finpilot.budgeting.domain.BudgetEnums.SavingsGoalStatus;
import com.finpilot.budgeting.dto.AddContributionRequest;
import com.finpilot.budgeting.dto.CreateSavingsGoalRequest;
import com.finpilot.budgeting.dto.SavingsGoalResponse;
import com.finpilot.budgeting.repository.SavingsContributionRepository;
import com.finpilot.budgeting.repository.SavingsGoalRepository;
import com.finpilot.budgeting.service.SavingsGoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SavingsGoalServiceTest {

    private SavingsGoalRepository savingsGoalRepository;
    private SavingsContributionRepository savingsContributionRepository;
    private SavingsGoalService savingsGoalService;

    private UUID userId;
    private UUID goalId;

    @BeforeEach
    void setUp() {
        savingsGoalRepository = mock(SavingsGoalRepository.class);
        savingsContributionRepository = mock(SavingsContributionRepository.class);
        savingsGoalService = new SavingsGoalService(savingsGoalRepository, savingsContributionRepository);

        userId = UUID.randomUUID();
        goalId = UUID.randomUUID();
    }

    @Test
    @DisplayName("createGoal: creates a new savings goal with target amount")
    void testCreateGoal() {
        CreateSavingsGoalRequest req = new CreateSavingsGoalRequest(
                "Emergency Fund",
                "Save 6 months of expenses",
                new BigDecimal("50000.00"),
                "INR",
                LocalDate.now().plusMonths(6)
        );

        SavingsGoal goal = new SavingsGoal(userId, "Emergency Fund", "Save 6 months of expenses", new BigDecimal("50000.00"), "INR", LocalDate.now().plusMonths(6));
        when(savingsGoalRepository.save(any(SavingsGoal.class))).thenReturn(goal);

        SavingsGoalResponse response = savingsGoalService.createGoal(userId, req);

        assertNotNull(response);
        assertEquals("Emergency Fund", response.title());
        assertEquals(new BigDecimal("50000.00"), response.targetAmount());
        assertEquals(new BigDecimal("0"), response.currentAmount());
        assertEquals(SavingsGoalStatus.IN_PROGRESS, response.status());
    }

    @Test
    @DisplayName("addContribution: updates current amount and auto-completes goal when target reached")
    void testAddContributionAutoCompletes() {
        SavingsGoal goal = new SavingsGoal(userId, "New Laptop", "MacBook Pro", new BigDecimal("150000.00"), "INR", null);
        goal.setCurrentAmount(new BigDecimal("120000.00"));

        when(savingsGoalRepository.findByIdAndUserId(goalId, userId)).thenReturn(Optional.of(goal));
        when(savingsGoalRepository.save(any(SavingsGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddContributionRequest req = new AddContributionRequest(new BigDecimal("30000.00"), "Final payment", LocalDate.now());
        SavingsGoalResponse response = savingsGoalService.addContribution(userId, goalId, req);

        assertNotNull(response);
        assertEquals(new BigDecimal("150000.00"), response.currentAmount());
        assertEquals(new BigDecimal("0.00"), response.remainingAmount());
        assertEquals(new BigDecimal("100.00"), response.percentageProgress());
        assertEquals(SavingsGoalStatus.COMPLETED, response.status());

        verify(savingsContributionRepository, times(1)).save(any(SavingsContribution.class));
    }
}
