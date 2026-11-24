package com.spendlens.audit;

import com.spendlens.insights.InsightsService;
import com.spendlens.statement.Category;
import com.spendlens.statement.CategorySource;
import com.spendlens.statement.StatementTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The agent's tools, as plain Java over one statement's transactions. Deterministic and read-only:
 * the model decides <em>which</em> tool to call and <em>what to conclude</em>; the numbers come from here.
 * The statement is fixed when the object is created, so the model can never reach another user's data.
 */
public class AuditTools {

    static final int MAX_RESULTS = 100;

    private final List<StatementTransaction> items;
    private final InsightsService insights;

    public AuditTools(List<StatementTransaction> items, InsightsService insights) {
        this.items = items;
        this.insights = insights;
    }

    // ---- result shapes (serialized to JSON for the model) ----

    public record Tx(Long id, LocalDate date, String description, Integer mcc, BigDecimal amount,
                     Category category, CategorySource source) {
        static Tx of(StatementTransaction t) {
            return new Tx(t.getId(), t.getDateTime().toLocalDate(), t.getDescription(), t.getMcc(), t.getAmount(),
                    t.getCategory(), t.getCategorySource());
        }
    }

    public record Overview(LocalDate from, LocalDate to, int transactions, BigDecimal income, BigDecimal expenses,
                           List<CategoryLine> expensesByCategory, Map<CategorySource, Long> categorizedBy) {
    }

    public record CategoryLine(Category category, BigDecimal total, int count) {
    }

    public record SearchResult(int totalMatches, int returned, List<Tx> transactions) {
    }

    public record DuplicateGroup(String merchant, BigDecimal amount, List<Tx> transactions) {
    }

    public record MerchantHistory(String query, int charges, BigDecimal total, List<Long> daysBetweenCharges,
                                  List<Tx> transactions) {
    }

    public record Recurring(List<Insight> detectedSubscriptions, List<RepeatedMerchant> otherRepeatedMerchants) {
        public record Insight(String merchant, BigDecimal typicalAmount, int charges, BigDecimal yearlyCost) {
        }

        public record RepeatedMerchant(String merchant, int charges, BigDecimal minAmount, BigDecimal maxAmount) {
        }
    }

    // ---- tools ----

    public Overview overview() {
        var i = insights.compute(items);
        var dates = items.stream().map(t -> t.getDateTime().toLocalDate()).sorted().toList();
        return new Overview(
                dates.isEmpty() ? null : dates.getFirst(),
                dates.isEmpty() ? null : dates.getLast(),
                items.size(), i.income(), i.expenses(),
                i.byCategory().stream().map(c -> new CategoryLine(c.category(), c.total(), c.count())).toList(),
                items.stream().filter(t -> t.getCategorySource() != null)
                        .collect(Collectors.groupingBy(StatementTransaction::getCategorySource, TreeMap::new,
                                Collectors.counting())));
    }

    public SearchResult search(String text, Category category, CategorySource source, String type,
                               BigDecimal minAmount, BigDecimal maxAmount, Integer limit) {
        String needle = text == null || text.isBlank() ? null : text.toLowerCase(Locale.ROOT).strip();
        var matches = items.stream()
                .filter(t -> needle == null || t.getDescription().toLowerCase(Locale.ROOT).contains(needle))
                .filter(t -> category == null || t.getCategory() == category)
                .filter(t -> source == null || t.getCategorySource() == source)
                .filter(t -> type == null || type.isBlank()
                        || ("EXPENSE".equalsIgnoreCase(type) == t.isExpense()))
                .filter(t -> minAmount == null || t.getAmount().abs().compareTo(minAmount) >= 0)
                .filter(t -> maxAmount == null || t.getAmount().abs().compareTo(maxAmount) <= 0)
                .sorted(Comparator.comparing(StatementTransaction::getDateTime))
                .toList();
        int max = limit == null || limit <= 0 ? 30 : Math.min(limit, MAX_RESULTS);
        var page = matches.stream().limit(max).map(Tx::of).toList();
        return new SearchResult(matches.size(), page.size(), page);
    }

    /** Same merchant, same amount, at most {@code maxDaysApart} days apart: likely double charges. */
    public List<DuplicateGroup> possibleDuplicates(Integer maxDaysApart) {
        int window = maxDaysApart == null || maxDaysApart < 0 ? 2 : Math.min(maxDaysApart, 31);
        var groups = items.stream().filter(StatementTransaction::isExpense)
                .collect(Collectors.groupingBy(t -> merchantKey(t) + "|" + t.getAmount().abs().toPlainString()));
        var result = new ArrayList<DuplicateGroup>();
        for (var group : groups.values()) {
            var sorted = group.stream().sorted(Comparator.comparing(StatementTransaction::getDateTime)).toList();
            var cluster = new ArrayList<StatementTransaction>();
            for (var t : sorted) {
                if (!cluster.isEmpty() && ChronoUnit.DAYS.between(
                        cluster.getLast().getDateTime().toLocalDate(), t.getDateTime().toLocalDate()) > window) {
                    addIfDuplicate(result, cluster);
                    cluster = new ArrayList<>();
                }
                cluster.add(t);
            }
            addIfDuplicate(result, cluster);
        }
        return result;
    }

    public MerchantHistory merchantHistory(String merchant) {
        String needle = merchant == null ? "" : InsightsService.merchantKey(merchant);
        var list = items.stream()
                .filter(t -> !needle.isEmpty() && merchantKey(t).contains(needle))
                .sorted(Comparator.comparing(StatementTransaction::getDateTime))
                .toList();
        var gaps = new ArrayList<Long>();
        for (int i = 1; i < list.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(list.get(i - 1).getDateTime(), list.get(i).getDateTime()));
        }
        BigDecimal total = list.stream().map(t -> t.getAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MerchantHistory(merchant, list.size(), total, gaps,
                list.stream().limit(MAX_RESULTS).map(Tx::of).toList());
    }

    public Recurring recurringCharges() {
        var detected = insights.compute(items).subscriptions().stream()
                .map(s -> new Recurring.Insight(s.merchant(), s.typicalAmount(), s.charges(), s.yearlyCost()))
                .toList();
        var detectedKeys = detected.stream().map(s -> InsightsService.merchantKey(s.merchant()))
                .collect(Collectors.toSet());
        var repeated = items.stream().filter(StatementTransaction::isExpense)
                .collect(Collectors.groupingBy(AuditTools::merchantKey))
                .entrySet().stream()
                .filter(e -> e.getValue().size() >= 2 && !detectedKeys.contains(e.getKey()))
                .map(e -> new Recurring.RepeatedMerchant(e.getValue().getFirst().getDescription(), e.getValue().size(),
                        e.getValue().stream().map(t -> t.getAmount().abs()).min(Comparator.naturalOrder()).orElseThrow(),
                        e.getValue().stream().map(t -> t.getAmount().abs()).max(Comparator.naturalOrder()).orElseThrow()))
                .sorted(Comparator.comparing(Recurring.RepeatedMerchant::charges).reversed())
                .toList();
        return new Recurring(detected, repeated);
    }

    /** Keeps only ids that belong to this statement, so a report can never point at made-up transactions. */
    public List<Long> knownIds(List<Long> ids) {
        if (ids == null) return List.of();
        var known = items.stream().map(StatementTransaction::getId).collect(Collectors.toSet());
        return ids.stream().filter(known::contains).distinct().toList();
    }

    private static void addIfDuplicate(List<DuplicateGroup> result, List<StatementTransaction> cluster) {
        if (cluster.size() >= 2) {
            result.add(new DuplicateGroup(cluster.getFirst().getDescription(), cluster.getFirst().getAmount().abs(),
                    cluster.stream().map(Tx::of).toList()));
        }
    }

    private static String merchantKey(StatementTransaction t) {
        return InsightsService.merchantKey(t.getDescription());
    }
}
