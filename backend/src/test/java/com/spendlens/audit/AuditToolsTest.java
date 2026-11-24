package com.spendlens.audit;

import com.spendlens.insights.InsightsService;
import com.spendlens.statement.Category;
import com.spendlens.statement.CategorySource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditToolsTest {

    private final AuditTools tools = new AuditTools(AuditFixtures.statement(), new InsightsService());

    @Test
    void overviewHasTotalsAndSources() {
        var o = tools.overview();
        assertThat(o.transactions()).isEqualTo(7);
        assertThat(o.income()).isEqualByComparingTo("40000");
        assertThat(o.expenses()).isEqualByComparingTo("2369");
        assertThat(o.categorizedBy()).containsEntry(CategorySource.AI, 5L).containsEntry(CategorySource.RULES, 2L);
        assertThat(o.from()).hasToString("2026-10-01");
    }

    @Test
    void searchCombinesFilters() {
        assertThat(tools.search(null, Category.OTHER, null, null, null, null, null).transactions())
                .extracting(AuditTools.Tx::id).containsExactly(4L);
        assertThat(tools.search("silpo", null, null, "EXPENSE", new BigDecimal("500"), null, null).totalMatches())
                .isEqualTo(2);
        assertThat(tools.search(null, null, CategorySource.RULES, "INCOME", null, null, null).transactions())
                .extracting(AuditTools.Tx::description).containsExactly("Salary");
    }

    @Test
    void searchIsCappedAtFiftyRows() {
        var many = new java.util.ArrayList<com.spendlens.statement.StatementTransaction>();
        for (int i = 0; i < 80; i++) {
            many.add(AuditFixtures.tx(i, "2026-11-01", "Coffee " + i, null, "-50", Category.RESTAURANTS,
                    CategorySource.AI));
        }
        var result = new AuditTools(many, new InsightsService()).search(null, null, null, null, null, null, 500);
        assertThat(result.totalMatches()).isEqualTo(80);
        assertThat(result.returned()).isEqualTo(50);
    }

    @Test
    void findsDoubleChargesWithinTheWindowOnly() {
        var groups = tools.possibleDuplicates(2);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().transactions()).extracting(AuditTools.Tx::id).containsExactly(5L, 6L);
        assertThat(tools.possibleDuplicates(0)).isEmpty(); // a day apart is outside a 0-day window
    }

    @Test
    void merchantHistoryShowsGapsBetweenCharges() {
        var h = tools.merchantHistory("netflix");
        assertThat(h.charges()).isEqualTo(2);
        assertThat(h.daysBetweenCharges()).containsExactly(31L);
        assertThat(h.total()).isEqualByComparingTo("598");
    }

    @Test
    void recurringChargesSeparatesDetectedFromCandidates() {
        var r = tools.recurringCharges();
        assertThat(r.detectedSubscriptions()).extracting(AuditTools.Recurring.Insight::merchant)
                .containsExactly("Netflix.com");
        assertThat(r.otherRepeatedMerchants()).extracting(AuditTools.Recurring.RepeatedMerchant::merchant)
                .containsExactly("Silpo");
    }

    @Test
    void knownIdsDropsMadeUpTransactions() {
        assertThat(tools.knownIds(List.of(4L, 999L, 4L, 6L))).containsExactly(4L, 6L);
        assertThat(tools.knownIds(null)).isEmpty();
    }
}
