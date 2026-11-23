package com.spendlens.ai;

import com.spendlens.config.AppProperties;
import com.spendlens.statement.Category;
import com.spendlens.statement.CategorySource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses a hand-written fake instead of the real Claude API: fast, free and deterministic. */
class CategorizationServiceTest {

    private static AppProperties props(int batchSize) {
        return new AppProperties(null, null, false, new AppProperties.Ai("key", "claude-opus-5-5", batchSize));
    }

    private static List<TransactionInput> inputs(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> new TransactionInput(i, "Bolt ride " + i, null, new BigDecimal("-100")))
                .toList();
    }

    /** Fake gateway: records batch sizes, answers ENTERTAINMENT, can skip indexes or fail. */
    static class FakeClaude implements ClaudeGateway {
        final List<Integer> batchSizes = new ArrayList<>();
        int failOnBatch = -1;
        int skipIndex = -1;

        @Override
        public List<CategoryAssignment> categorize(List<TransactionInput> batch) {
            batchSizes.add(batch.size());
            if (batchSizes.size() - 1 == failOnBatch) throw new RuntimeException("API overloaded");
            return batch.stream()
                    .filter(t -> t.index() != skipIndex)
                    .map(t -> new CategoryAssignment(t.index(), Category.ENTERTAINMENT))
                    .toList();
        }

        @Override
        public String answer(String question, String context) {
            return "";
        }
    }

    @Test
    void withoutClaudeEverythingIsCategorizedByRules() {
        var service = new CategorizationService(Optional.empty(), new RuleBasedCategorizer(), props(10));

        var results = service.categorize(inputs(3));

        assertThat(service.aiEnabled()).isFalse();
        assertThat(results).hasSize(3).allSatisfy(r -> {
            assertThat(r.source()).isEqualTo(CategorySource.RULES);
            assertThat(r.category()).isEqualTo(Category.TRANSPORT);
        });
    }

    @Test
    void sendsTransactionsToClaudeInBatches() {
        var fake = new FakeClaude();
        var service = new CategorizationService(Optional.of(fake), new RuleBasedCategorizer(), props(4));

        var results = service.categorize(inputs(10));

        assertThat(fake.batchSizes).containsExactly(4, 4, 2);
        assertThat(results).hasSize(10).allSatisfy(r -> {
            assertThat(r.source()).isEqualTo(CategorySource.AI);
            assertThat(r.category()).isEqualTo(Category.ENTERTAINMENT);
        });
    }

    @Test
    void failedBatchFallsBackToRulesOthersStayAi() {
        var fake = new FakeClaude();
        fake.failOnBatch = 1;
        var service = new CategorizationService(Optional.of(fake), new RuleBasedCategorizer(), props(2));

        var results = service.categorize(inputs(6));

        assertThat(results).extracting(CategorizationService.Result::source).containsExactly(
                CategorySource.AI, CategorySource.AI,
                CategorySource.RULES, CategorySource.RULES,
                CategorySource.AI, CategorySource.AI);
    }

    @Test
    void transactionSkippedByTheModelIsFilledByRules() {
        var fake = new FakeClaude();
        fake.skipIndex = 1;
        var service = new CategorizationService(Optional.of(fake), new RuleBasedCategorizer(), props(10));

        var results = service.categorize(inputs(3));

        assertThat(results.get(1).source()).isEqualTo(CategorySource.RULES);
        assertThat(results.get(1).category()).isEqualTo(Category.TRANSPORT);
        assertThat(results).extracting(CategorizationService.Result::index).containsExactly(0, 1, 2);
    }
}
