package com.spendlens.statement;

/** Published after a statement is saved; processed asynchronously once the transaction commits. */
public record StatementUploaded(Long statementId) {
}
