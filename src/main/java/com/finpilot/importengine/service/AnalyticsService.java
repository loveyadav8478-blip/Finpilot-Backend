package com.finpilot.importengine.service;

import com.finpilot.transaction.domain.Category;
import com.finpilot.transaction.domain.Transaction;
import com.finpilot.transaction.domain.TransactionEnums;
import com.finpilot.transaction.repository.CategoryRepository;
import com.finpilot.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public AnalyticsService(TransactionRepository transactionRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    public Map<String, BigDecimal> getSpendingByCategory(UUID userId, LocalDate start, LocalDate end) {
        List<Transaction> transactions =
                transactionRepository.findByUserIdAndTransactionDateBetweenAndDeletedFalse(userId, start, end);

        // Build the UUID -> code lookup ONCE, upfront — not one query per
        // transaction inside the loop (that's an N+1 query problem).
        Map<UUID, String> categoryCodesById = new HashMap<>();
        for (Category category : categoryRepository.findAll()) {
            categoryCodesById.put(category.getId(), category.getCode());
        }

        Map<String, BigDecimal> categoryWiseExpense = new HashMap<>();

        for (Transaction transaction : transactions) {
            // Only spending counts here — CREDIT (salary, refunds, etc.)
            // would otherwise inflate the totals.
            if (transaction.getDirection() != TransactionEnums.Direction.DEBIT) {
                continue;
            }

            String category = transaction.getCategoryId() != null
                    ? categoryCodesById.getOrDefault(transaction.getCategoryId(), "UNKNOWN")
                    : "UNCATEGORIZED";

            BigDecimal oldAmount = categoryWiseExpense.getOrDefault(category, BigDecimal.ZERO);
            categoryWiseExpense.put(category, oldAmount.add(transaction.getAmount()));
        }

        return categoryWiseExpense;
    }
}