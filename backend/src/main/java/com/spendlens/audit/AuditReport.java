package com.spendlens.audit;

import com.spendlens.statement.Category;

import java.util.List;

/** What the agent hands back via the submit_report tool (after transaction ids were validated). */
public record AuditReport(String summary, List<Finding> findings, boolean complete) {

    public enum FindingType { MISCATEGORIZED, DUPLICATE, SUBSCRIPTION, UNUSUAL_EXPENSE, SAVING_TIP }

    public enum Severity { LOW, MEDIUM, HIGH }

    public record Finding(FindingType type, Severity severity, String title, String details,
                          List<Long> transactionIds, Category suggestedCategory) {
    }
}
