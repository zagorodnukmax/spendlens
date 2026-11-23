package com.spendlens.insights;

import com.spendlens.statement.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything the dashboard shows for one statement. All expense amounts are positive numbers. */
public record Insights(
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal balance,
        int transactionCount,
        List<CategoryTotal> byCategory,
        List<MonthTotal> byMonth,
        List<MerchantTotal> topMerchants,
        List<Subscription> subscriptions,
        List<Anomaly> anomalies) {

    public record CategoryTotal(Category category, String label, String color, BigDecimal total,
                                int count, double share) {
    }

    public record MonthTotal(String month, BigDecimal income, BigDecimal expenses) {
    }

    public record MerchantTotal(String merchant, BigDecimal total, int count) {
    }

    /** A recurring charge: a known subscription service charged 2+ times, or the same merchant charged 3+ times monthly with a stable amount. */
    public record Subscription(String merchant, BigDecimal typicalAmount, int charges, LocalDate lastCharge,
                               BigDecimal yearlyCost) {
    }

    /** An expense that is much larger than usual for its category. */
    public record Anomaly(Long transactionId, LocalDate date, String description, BigDecimal amount,
                          Category category, String reason) {
    }
}
