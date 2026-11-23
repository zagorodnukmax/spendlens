package com.spendlens.ai;

import java.util.List;

/**
 * The only place that talks to the LLM. Kept as an interface so business logic can be unit-tested
 * with a fake, and so the provider can be swapped without touching the services.
 */
public interface ClaudeGateway {

    /** Assigns a category to every input. May return fewer items than requested; callers must handle gaps. */
    List<CategoryAssignment> categorize(List<TransactionInput> batch);

    /** Answers a free-form question about the statement, using only the given context. */
    String answer(String question, String statementContext);
}
