package com.spendlens.insights;

import com.spendlens.statement.Category;
import com.spendlens.statement.CategorySource;
import com.spendlens.statement.ParsedTransaction;
import com.spendlens.statement.StatementTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InsightsServiceTest {

    private final InsightsService service = new InsightsService();

    private static StatementTransaction tx(String date, String description, String amount, Category category) {
        var t = new StatementTransaction(null, new ParsedTransaction(
                LocalDate.parse(date).atTime(12, 0), description, null, new BigDecimal(amount)));
        t.categorize(category, CategorySource.RULES);
        return t;
    }

    @Test
    void totalsAndCategoryShares() {
        var insights = service.compute(List.of(
                tx("2026-11-01", "Salary", "1000", Category.INCOME),
                tx("2026-11-02", "Silpo", "-300", Category.GROCERIES),
                tx("2026-11-03", "Bolt", "-100", Category.TRANSPORT)));

        assertThat(insights.income()).isEqualByComparingTo("1000");
        assertThat(insights.expenses()).isEqualByComparingTo("400");
        assertThat(insights.balance()).isEqualByComparingTo("600");
        assertThat(insights.byCategory()).extracting(Insights.CategoryTotal::category)
                .containsExactly(Category.GROCERIES, Category.TRANSPORT);
        assertThat(insights.byCategory().getFirst().share()).isEqualTo(0.75);
    }

    @Test
    void groupsByMonth() {
        var months = service.compute(List.of(
                tx("2026-10-05", "Silpo", "-100", Category.GROCERIES),
                tx("2026-11-01", "Salary", "500", Category.INCOME),
                tx("2026-11-02", "Silpo", "-50", Category.GROCERIES))).byMonth();

        assertThat(months).extracting(Insights.MonthTotal::month).containsExactly("2026-10", "2026-11");
        assertThat(months.get(1).income()).isEqualByComparingTo("500");
        assertThat(months.get(1).expenses()).isEqualByComparingTo("50");
    }

    @Test
    void detectsMonthlyChargeWithSimilarAmountsAsSubscription() {
        var subs = service.compute(List.of(
                tx("2026-09-10", "Kyivstar #123", "-250", Category.UTILITIES),
                tx("2026-10-10", "Kyivstar #456", "-250", Category.UTILITIES),
                tx("2026-11-10", "Kyivstar #789", "-260", Category.UTILITIES))).subscriptions();

        assertThat(subs).hasSize(1);
        assertThat(subs.getFirst().charges()).isEqualTo(3);
        assertThat(subs.getFirst().typicalAmount()).isEqualByComparingTo("250");
        assertThat(subs.getFirst().yearlyCost()).isEqualByComparingTo("3000");
    }

    @Test
    void irregularOrOneOffChargesAreNotSubscriptions() {
        var subs = service.compute(List.of(
                // same shop, but weekly and different amounts
                tx("2026-11-01", "Silpo", "-300", Category.GROCERIES),
                tx("2026-11-08", "Silpo", "-650", Category.GROCERIES),
                // one-off digital purchase
                tx("2026-11-18", "Steam", "-449", Category.SUBSCRIPTIONS))).subscriptions();

        assertThat(subs).isEmpty();
    }

    @Test
    void twoSimilarPaymentsAMonthApartAreNotEnoughForOrdinaryMerchants() {
        // Regression: fuel and taxi were reported as subscriptions.
        var subs = service.compute(List.of(
                tx("2026-09-15", "WOG", "-1450", Category.TRANSPORT),
                tx("2026-10-18", "WOG", "-1380", Category.TRANSPORT),
                tx("2026-09-04", "Bolt", "-184", Category.TRANSPORT),
                tx("2026-10-08", "Bolt", "-190", Category.TRANSPORT))).subscriptions();
        assertThat(subs).isEmpty();
    }

    @Test
    void knownSubscriptionServiceNeedsTwoChargesButNotExactInterval() {
        var subs = service.compute(List.of(
                tx("2026-10-03", "Netflix.com", "-299", Category.SUBSCRIPTIONS),
                tx("2026-11-20", "Netflix.com", "-349", Category.SUBSCRIPTIONS))).subscriptions();
        assertThat(subs).hasSize(1);
    }

    @Test
    void flagsExpenseMuchLargerThanCategoryMedian() {
        var items = new ArrayList<StatementTransaction>();
        for (int i = 1; i <= 5; i++) items.add(tx("2026-11-0" + i, "Silpo", "-600", Category.GROCERIES));
        items.add(tx("2026-11-11", "Silpo", "-4870.50", Category.GROCERIES));

        var anomalies = service.compute(items).anomalies();

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.getFirst().amount()).isEqualByComparingTo("4870.50");
        assertThat(anomalies.getFirst().reason()).startsWith("8.1×").contains("Groceries");
    }

    @Test
    void noAnomaliesForSmallAmountsTooFewSamplesOrTransfers() {
        var items = new ArrayList<StatementTransaction>();
        for (int i = 1; i <= 5; i++) items.add(tx("2026-11-0" + i, "Coffee", "-25", Category.RESTAURANTS));
        items.add(tx("2026-11-09", "Coffee", "-120", Category.RESTAURANTS));         // 4.8x but < 500 UAH
        items.add(tx("2026-11-01", "Booking", "-6400", Category.TRAVEL));             // single sample
        for (int i = 1; i <= 4; i++) items.add(tx("2026-11-0" + i, "Transfer", "-100", Category.TRANSFERS));
        items.add(tx("2026-11-09", "Transfer", "-9000", Category.TRANSFERS));         // transfers are ignored

        assertThat(service.compute(items).anomalies()).isEmpty();
    }

    @Test
    void topMerchantsMergeDescriptionsThatDifferOnlyByNumbers() {
        var top = service.compute(List.of(
                tx("2026-11-01", "WOG 1234", "-1000", Category.TRANSPORT),
                tx("2026-11-02", "WOG 5678", "-500", Category.TRANSPORT),
                tx("2026-11-03", "Silpo", "-300", Category.GROCERIES))).topMerchants();

        assertThat(top.getFirst().total()).isEqualByComparingTo("1500");
        assertThat(top.getFirst().count()).isEqualTo(2);
    }

    @Test
    void medianOfEvenAndOddLists() {
        assertThat(InsightsService.median(List.of(new BigDecimal("1"), new BigDecimal("3"), new BigDecimal("2"))))
                .isEqualByComparingTo("2");
        assertThat(InsightsService.median(List.of(new BigDecimal("1"), new BigDecimal("4"))))
                .isEqualByComparingTo("2.5");
        assertThat(InsightsService.median(List.of())).isEqualByComparingTo("0");
    }
}
