package com.spendlens.audit;

import com.spendlens.statement.StatementTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Runs the agent in the background. No database transaction is held while Claude works (that can take
 * a minute); each step and the final result are saved in their own short transactions instead.
 */
@Component
public class AuditExecutor {

    private static final Logger log = LoggerFactory.getLogger(AuditExecutor.class);

    private final Optional<AuditAgent> agent;
    private final AuditRunRepository runs;
    private final AuditStepRepository steps;
    private final StatementTransactionRepository transactions;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    public AuditExecutor(Optional<AuditAgent> agent, AuditRunRepository runs, AuditStepRepository steps,
                         StatementTransactionRepository transactions, TransactionTemplate tx, JsonMapper json) {
        this.agent = agent;
        this.runs = runs;
        this.steps = steps;
        this.transactions = transactions;
        this.tx = tx;
        this.json = json;
    }

    @Async
    @TransactionalEventListener
    public void onRequested(AuditRequested event) {
        execute(event.runId());
    }

    void execute(Long runId) {
        try {
            var items = tx.execute(s -> {
                var run = runs.findById(runId).orElseThrow();
                return transactions.findByStatementIdOrderByDateTimeDesc(run.getStatement().getId());
            });
            var result = agent.orElseThrow().run(items, step -> tx.executeWithoutResult(s ->
                    steps.save(new AuditStep(runs.getReferenceById(runId), step))));
            String report = json.writeValueAsString(result.report());
            tx.executeWithoutResult(s -> runs.findById(runId).orElseThrow()
                    .finish(report, result.inputTokens(), result.outputTokens()));
            log.info("Audit {} done: {} findings, {} in / {} out tokens", runId,
                    result.report().findings().size(), result.inputTokens(), result.outputTokens());
        } catch (RuntimeException e) {
            log.error("Audit {} failed", runId, e);
            tx.executeWithoutResult(s -> runs.findById(runId).ifPresent(r -> r.fail(e.getMessage())));
        }
    }
}
