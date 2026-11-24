package com.spendlens.audit;

import com.spendlens.statement.Category;
import com.spendlens.statement.CategorySource;
import com.spendlens.statement.ParsedTransaction;
import com.spendlens.statement.StatementTransaction;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A small statement with one miscategorized row, one double charge and a monthly subscription. */
final class AuditFixtures {

    private AuditFixtures() {
    }

    static StatementTransaction tx(long id, String date, String description, Integer mcc, String amount,
                                   Category category, CategorySource source) {
        var t = new StatementTransaction(null, new ParsedTransaction(LocalDate.parse(date).atTime(12, 0),
                description, mcc, new BigDecimal(amount)));
        t.categorize(category, source);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    static List<StatementTransaction> statement() {
        return List.of(
                tx(1, "2026-10-01", "Salary", null, "40000", Category.INCOME, CategorySource.RULES),
                tx(2, "2026-10-03", "Netflix.com", 4899, "-299", Category.SUBSCRIPTIONS, CategorySource.AI),
                tx(3, "2026-11-03", "Netflix.com", 4899, "-299", Category.SUBSCRIPTIONS, CategorySource.AI),
                tx(4, "2026-11-05", "Uklon trip", null, "-180", Category.OTHER, CategorySource.RULES),
                tx(5, "2026-11-07", "Silpo", 5411, "-640.50", Category.GROCERIES, CategorySource.AI),
                tx(6, "2026-11-08", "Silpo", 5411, "-640.50", Category.GROCERIES, CategorySource.AI),
                tx(7, "2026-11-20", "Silpo", 5411, "-310", Category.GROCERIES, CategorySource.AI));
    }
}
