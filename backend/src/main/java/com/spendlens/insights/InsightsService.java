package com.spendlens.insights;

import com.spendlens.insights.Insights.*;
import com.spendlens.statement.Category;
import com.spendlens.statement.StatementTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure, deterministic statistics over a statement's transactions. No AI here: numbers must be exact
 * and reproducible. Claude is only used to categorize and to answer questions.
 */
@Service
public class InsightsService {

    /** An expense is an anomaly if it is this many times larger than the category median... */
    static final BigDecimal ANOMALY_FACTOR = new BigDecimal("3");
    /** ...and at least this big (avoid flagging a 90 UAH coffee next to 25 UAH ones). */
    static final BigDecimal ANOMALY_MIN_AMOUNT = new BigDecimal("500");
    /** Categories need this many expenses for a meaningful median. */
    static final int ANOMALY_MIN_SAMPLES = 4;

    public Insights compute(List<StatementTransaction> items) {
        var expenses = items.stream().filter(StatementTransaction::isExpense).toList();

        BigDecimal income = items.stream().filter(t -> !t.isExpense())
                .map(StatementTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = expenses.stream().map(InsightsService::abs).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Insights(income, spent, income.subtract(spent), items.size(),
                byCategory(expenses, spent), byMonth(items), topMerchants(expenses),
                subscriptions(expenses), anomalies(expenses));
    }

    List<CategoryTotal> byCategory(List<StatementTransaction> expenses, BigDecimal spent) {
        Map<Category, List<StatementTransaction>> groups = expenses.stream()
                .collect(Collectors.groupingBy(InsightsService::categoryOf));
        return groups.entrySet().stream()
                .map(e -> {
                    BigDecimal total = sum(e.getValue());
                    double share = spent.signum() == 0 ? 0
                            : total.divide(spent, 4, RoundingMode.HALF_UP).doubleValue();
                    return new CategoryTotal(e.getKey(), e.getKey().label(), e.getKey().color(), total,
                            e.getValue().size(), share);
                })
                .sorted(Comparator.comparing(CategoryTotal::total).reversed())
                .toList();
    }

    List<MonthTotal> byMonth(List<StatementTransaction> items) {
        var months = new TreeMap<YearMonth, BigDecimal[]>();
        for (var t : items) {
            var totals = months.computeIfAbsent(YearMonth.from(t.getDateTime()),
                    m -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (t.isExpense()) totals[1] = totals[1].add(abs(t));
            else totals[0] = totals[0].add(t.getAmount());
        }
        return months.entrySet().stream()
                .map(e -> new MonthTotal(e.getKey().toString(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    List<MerchantTotal> topMerchants(List<StatementTransaction> expenses) {
        return expenses.stream()
                .collect(Collectors.groupingBy(t -> merchantKey(t.getDescription()), LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .map(group -> new MerchantTotal(group.getFirst().getDescription(), sum(group), group.size()))
                .sorted(Comparator.comparing(MerchantTotal::total).reversed())
                .limit(5)
                .toList();
    }

    List<Subscription> subscriptions(List<StatementTransaction> expenses) {
        var result = new ArrayList<Subscription>();
        var groups = expenses.stream()
                .collect(Collectors.groupingBy(t -> merchantKey(t.getDescription())));
        for (var group : groups.values()) {
            var sorted = group.stream().sorted(Comparator.comparing(StatementTransaction::getDateTime)).toList();
            BigDecimal typical = median(sorted.stream().map(InsightsService::abs).toList());
            boolean knownService = sorted.stream().allMatch(t -> categoryOf(t) == Category.SUBSCRIPTIONS);
            if (sorted.size() >= 2 && (knownService || looksMonthly(sorted, typical))) {
                result.add(new Subscription(sorted.getLast().getDescription(), typical, sorted.size(),
                        sorted.getLast().getDateTime().toLocalDate(), typical.multiply(BigDecimal.valueOf(12))));
            }
        }
        result.sort(Comparator.comparing(Subscription::yearlyCost).reversed());
        return result;
    }

    /**
     * For merchants that are not known subscription services: at least three charges, amounts within
     * ±5% of the median, 25–35 days apart. Two similar fuel or taxi payments a month apart are common,
     * so two charges are not enough evidence.
     */
    static boolean looksMonthly(List<StatementTransaction> sorted, BigDecimal typical) {
        if (sorted.size() < 3 || typical.signum() == 0) return false;
        BigDecimal tolerance = typical.multiply(new BigDecimal("0.05"));
        boolean similarAmounts = sorted.stream()
                .allMatch(t -> abs(t).subtract(typical).abs().compareTo(tolerance) <= 0);
        if (!similarAmounts) return false;
        for (int i = 1; i < sorted.size(); i++) {
            long days = ChronoUnit.DAYS.between(sorted.get(i - 1).getDateTime(), sorted.get(i).getDateTime());
            if (days < 25 || days > 35) return false;
        }
        return true;
    }

    List<Anomaly> anomalies(List<StatementTransaction> expenses) {
        var result = new ArrayList<Anomaly>();
        var groups = expenses.stream().collect(Collectors.groupingBy(InsightsService::categoryOf));
        for (var entry : groups.entrySet()) {
            var group = entry.getValue();
            if (group.size() < ANOMALY_MIN_SAMPLES || entry.getKey() == Category.TRANSFERS) continue;
            BigDecimal median = median(group.stream().map(InsightsService::abs).toList());
            if (median.signum() == 0) continue;
            for (var t : group) {
                BigDecimal amount = abs(t);
                if (amount.compareTo(ANOMALY_MIN_AMOUNT) >= 0
                        && amount.compareTo(median.multiply(ANOMALY_FACTOR)) >= 0) {
                    BigDecimal times = amount.divide(median, 1, RoundingMode.HALF_UP);
                    result.add(new Anomaly(t.getId(), t.getDateTime().toLocalDate(), t.getDescription(), amount,
                            entry.getKey(), "%s× your typical %s expense (%s UAH)"
                            .formatted(times.toPlainString(), entry.getKey().label(), median.toPlainString())));
                }
            }
        }
        result.sort(Comparator.comparing(Anomaly::amount).reversed());
        return result;
    }

    // ---- helpers ----

    /** Normalizes a bank description to a merchant key: lower case, no digits/punctuation, single spaces. */
    public static String merchantKey(String description) {
        return description.toLowerCase(Locale.ROOT)
                .replaceAll("[0-9*#№]+", " ")
                .replaceAll("[^\\p{L} ]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        var sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid)
                : sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
    }

    private static Category categoryOf(StatementTransaction t) {
        return t.getCategory() == null ? Category.OTHER : t.getCategory();
    }

    private static BigDecimal abs(StatementTransaction t) {
        return t.getAmount().abs();
    }

    private static BigDecimal sum(List<StatementTransaction> items) {
        return items.stream().map(InsightsService::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
