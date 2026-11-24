package com.spendlens.audit;

/** One tool call made by the agent, as shown in the UI ("🔧 search_transactions {category: OTHER} → 4 matches"). */
public record AuditStepInfo(String tool, String input, String outcome) {
}
