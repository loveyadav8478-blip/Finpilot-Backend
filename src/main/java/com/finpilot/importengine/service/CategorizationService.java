package com.finpilot.importengine.service;

import com.finpilot.importengine.dto.CategorizationRequestItem;
import com.finpilot.importengine.dto.CategorizationResultItem;
import com.finpilot.transaction.domain.Category;
import com.finpilot.transaction.domain.Transaction;
import com.finpilot.transaction.domain.TransactionEnums;
import com.finpilot.transaction.repository.CategoryRepository;
import com.finpilot.transaction.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Finds uncategorized transactions for a user, sends them to the AI
// service, and applies the results. Keeps HTTP details out of this class —
// that's CategorizationClient's job.
@Service
public class CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationClient categorizationClient;

    public CategorizationService(TransactionRepository transactionRepository,
                                 CategoryRepository categoryRepository,
                                 CategorizationClient categorizationClient) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.categorizationClient = categorizationClient;
    }

    @Transactional
    public int categorizeUncategorized(UUID userId) {
        List<Transaction> uncategorized = transactionRepository.findUncategorized(userId);

        if (uncategorized.isEmpty()) {
            log.info("No uncategorized transactions found for user {}", userId);
            return 0;
        }

        log.info("Found {} uncategorized transactions for user {}", uncategorized.size(), userId);

        List<CategorizationRequestItem> requestItems = uncategorized.stream()
                .map(t -> new CategorizationRequestItem(
                        t.getId().toString(),
                        t.getDescription() != null ? t.getDescription() : t.getMerchantRaw(),
                        t.getAmount().doubleValue()))
                .collect(Collectors.toList());

        List<CategorizationResultItem> results;
        long startedAt = System.currentTimeMillis();
        log.info("Calling AI categorization service for user {}...", userId);
        try {
            results = categorizationClient.categorize(requestItems);
        } catch (CategorizationServiceUnavailableException e) {
            // Fail soft: leave these transactions uncategorized rather than
            // failing the whole import/request. They'll be picked up again
            // next time categorizeUncategorized runs — findUncategorized
            // only returns rows with categoryId still null.
            log.error("AI categorization service call failed for user {} — leaving {} transactions uncategorized: {}",
                    userId, uncategorized.size(), e.getMessage());
            return 0;
        }
        log.info("AI service responded in {}ms for user {}", System.currentTimeMillis() - startedAt, userId);

        // index transactions by id (as string) so we can match results back to entities
        Map<String, Transaction> byId = uncategorized.stream()
                .collect(Collectors.toMap(t -> t.getId().toString(), t -> t));

        int applied = 0;
        for (CategorizationResultItem result : results) {
            Transaction transaction = byId.get(result.getTransactionId());
            if (transaction == null) {
                log.warn("AI service returned a result for unknown transaction_id {} — skipping", result.getTransactionId());
                continue; // shouldn't happen, but don't let one bad match blow up the batch
            }

            Category category = categoryRepository.findByCode(result.getCategory()).orElse(null);
            if (category == null) {
                log.warn("AI service returned unknown category code '{}' for transaction {} — leaving uncategorized",
                        result.getCategory(), transaction.getId());
                continue; // unknown category code — skip rather than fail the whole loop
            }

            TransactionEnums.CategorizationSource source =
                    TransactionEnums.CategorizationSource.valueOf(result.getSource());

            transaction.applyCategorization(category.getId(), BigDecimal.valueOf(result.getConfidence()), source);
            transactionRepository.save(transaction);
            applied++;
        }

        log.info("Categorization completed for user {}: {} categorized, {} skipped", userId, applied, uncategorized.size() - applied);

        return applied;
    }
}