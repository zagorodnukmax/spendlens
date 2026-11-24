package com.spendlens.audit;

import java.time.Instant;
import java.util.List;

public record AuditRunResponse(Long id, AuditStatus status, Instant startedAt, Instant finishedAt, String error,
                               List<Step> steps, AuditReport report, long inputTokens, long outputTokens) {

    public record Step(String tool, String input, String outcome, Instant at) {
    }
}
