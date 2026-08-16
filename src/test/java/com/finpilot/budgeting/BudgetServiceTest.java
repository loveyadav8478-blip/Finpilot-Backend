package com.finpilot.budgeting;

import com.finpilot.budgeting.domain.Budget;
import com.finpilot.budgeting.domain.BudgetEnums.BudgetAlertStatus;
import com.finpilot.budgeting.dto.BudgetOverviewResponse;
import com.finpilot.budgeting.dto.BudgetResponse;
import com.finpilot.budgeting.dto.BudgetStatusItem;
import com.finpilot.budgeting.dto.CreateBudgetRequest;
import com.finpilot.budgeting.repository.BudgetRepository;
import com.finpilot.budgeting.service.BudgetService;
import com.finpilot.transaction.domain.Category;
import com.finpilot.transaction.repository.CategoryRepository;
import com.finpilot.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BudgetServiceTest {

    private BudgetRepository budgetRepository;
    private CategoryRepository categoryRepository;
    private TransactionRepository transactionRepository;
    private BudgetService budgetService;

    private UUID userId;
    private UUID categoryId;
    private Category category;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(BudgetRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        budgetService = new BudgetService(budgetRepository, categoryRepository, transactionRepository);

        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        category = new Category("FOOD", "Food");
        // Set id via reflection or mocking
    }

    @Test
    @DisplayName("createOrUpdateBudget: throws exception when monthly limit is negative or zero")
    void testCreateBudgetInvalidLimit() {
        CreateBudgetRequest req = new CreateBudgetRequest(categoryId, BigDecimal.ZERO, "2026-08", new BigDecimal("80.00"), "INR");
        assertThrows(IllegalArgumentException.class, () -> budgetService.createOrUpdateBudget(userId, req));
    }

    @Test
    @DisplayName("createOrUpdateBudget: creates new budget when none exists")
    void testCreateNewBudget() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(budgetRepository.findByUserIdAndCategoryIdAndPeriodMonth(eq(userId), eq(categoryId), eq("2026-08")))
                .thenReturn(Optional.empty());

        Budget savedBudget = new Budget(userId, category, new BigDecimal("10000.00"), "2026-08", new BigDecimal("80.00"), "INR");
        when(budgetRepository.save(any(Budget.class))).thenReturn(savedBudget);

        CreateBudgetRequest req = new CreateBudgetRequest(categoryId, new BigDecimal("10000.00"), "2026-08", new BigDecimal("80.00"), "INR");
        BudgetResponse response = budgetService.createOrUpdateBudget(userId, req);

        assertNotNull(response);
        assertEquals(new BigDecimal("10000.00"), response.monthlyLimitAmount());
        assertEquals("2026-08", response.periodMonth());
        verify(budgetRepository, times(1)).save(any(Budget.class));
    }

    @Test
    @DisplayName("getMonthlyBudgetOverview: calculates spent amount, percentages, and EXCEEDED alert status")
    void testGetMonthlyBudgetOverviewExceeded() {
        Budget budget = new Budget(userId, category, new BigDecimal("5000.00"), "2026-08", new BigDecimal("80.00"), "INR");
        when(budgetRepository.findByUserIdAndPeriodMonth(userId, "2026-08")).thenReturn(List.of(budget));

        // Mock spending query returning 6000.00 (spent > limit)
        List<Object[]> rawSpends = List.of(new Object[]{category.getId(), new BigDecimal("6000.00")});
        when(transactionRepository.sumDebitAmountsByUserIdAndCategoryAndDateRange(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(rawSpends);

        BudgetOverviewResponse overview = budgetService.getMonthlyBudgetOverview(userId, "2026-08");

        assertNotNull(overview);
        assertEquals("2026-08", overview.periodMonth());
        assertEquals(1, overview.exceededCount());
        assertEquals(0, overview.warningCount());

        BudgetStatusItem item = overview.budgetItems().get(0);
        assertEquals(new BigDecimal("6000.00"), item.spentAmount());
        assertEquals(new BigDecimal("-1000.00"), item.remainingAmount());
        assertEquals(BudgetAlertStatus.EXCEEDED, item.alertStatus());
    }

    @Test
    @DisplayName("getMonthlyBudgetOverview: detects WARNING alert status when spending crosses threshold")
    void testGetMonthlyBudgetOverviewWarning() {
        Budget budget = new Budget(userId, category, new BigDecimal("10000.00"), "2026-08", new BigDecimal("80.00"), "INR");
        when(budgetRepository.findByUserIdAndPeriodMonth(userId, "2026-08")).thenReturn(List.of(budget));

        // Mock spending query returning 8500.00 (85% > 80% threshold, but < 100%)
        List<Object[]> rawSpends = List.of(new Object[]{category.getId(), new BigDecimal("8500.00")});
        when(transactionRepository.sumDebitAmountsByUserIdAndCategoryAndDateRange(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(rawSpends);

        BudgetOverviewResponse overview = budgetService.getMonthlyBudgetOverview(userId, "2026-08");

        assertNotNull(overview);
        assertEquals(0, overview.exceededCount());
        assertEquals(1, overview.warningCount());

        BudgetStatusItem item = overview.budgetItems().get(0);
        assertEquals(BudgetAlertStatus.WARNING, item.alertStatus());
        assertEquals(new BigDecimal("1500.00"), item.remainingAmount());
    }
}
