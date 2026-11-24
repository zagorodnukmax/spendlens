package com.spendlens.audit;

/** Published when a run is created; executed in the background after the transaction commits. */
public record AuditRequested(Long runId) {
}
