package com.spendlens.ai;

import com.spendlens.config.AppProperties;
import com.spendlens.statement.Category;
import com.spendlens.statement.CategorySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Categorizes transactions in batches. Claude is tried first (if configured); any batch that fails,
 * and any transaction the model skipped, falls back to {@link RuleBasedCategorizer}. The result is
 * therefore always complete, and each item records who categorized it.
 */
@Service
public class CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);

    public record Result(int index, Category category, CategorySource source) {
    }

    private final Optional<ClaudeGateway> claude;
    private final RuleBasedCategorizer rules;
    private final int batchSize;

    public CategorizationService(Optional<ClaudeGateway> claude, RuleBasedCategorizer rules, AppProperties props) {
        this.claude = claude;
        this.rules = rules;
        this.batchSize = Math.max(1, props.ai().batchSize());
    }

    public boolean aiEnabled() {
        return claude.isPresent();
    }

    public List<Result> categorize(List<TransactionInput> inputs) {
        var results = new ArrayList<Result>(inputs.size());
        for (int from = 0; from < inputs.size(); from += batchSize) {
            results.addAll(categorizeBatch(inputs.subList(from, Math.min(from + batchSize, inputs.size()))));
        }
        return results;
    }

    private List<Result> categorizeBatch(List<TransactionInput> batch) {
        Map<Integer, Category> fromAi = new HashMap<>();
        if (claude.isPresent()) {
            try {
                for (var a : claude.get().categorize(batch)) {
                    fromAi.putIfAbsent(a.index(), a.category());
                }
            } catch (RuntimeException e) {
                log.warn("Claude categorization failed for a batch of {}, using rules: {}", batch.size(), e.toString());
            }
        }
        return batch.stream()
                .map(t -> fromAi.containsKey(t.index())
                        ? new Result(t.index(), fromAi.get(t.index()), CategorySource.AI)
                        : new Result(t.index(), rules.categorize(t), CategorySource.RULES))
                .toList();
    }
}
