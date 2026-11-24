package com.spendlens.audit;

import com.spendlens.statement.StatementTransaction;

import java.util.List;
import java.util.function.Consumer;

/** Runs one audit. Implemented with Claude; tests substitute a fake. */
public interface AuditAgent {

    record Result(AuditReport report, long inputTokens, long outputTokens) {
    }

    Result run(List<StatementTransaction> transactions, Consumer<AuditStepInfo> onStep);
}
