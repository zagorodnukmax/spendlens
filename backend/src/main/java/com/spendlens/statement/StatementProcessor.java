package com.spendlens.statement;

import com.spendlens.ai.CategorizationService;
import com.spendlens.ai.TransactionInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Categorizes an uploaded statement in the background, so the upload request returns immediately
 * and the UI polls the status (PROCESSING → DONE / FAILED).
 */
@Component
public class StatementProcessor {

    private static final Logger log = LoggerFactory.getLogger(StatementProcessor.class);

    private final StatementRepository statements;
    private final StatementTransactionRepository transactions;
    private final CategorizationService categorization;

    public StatementProcessor(StatementRepository statements, StatementTransactionRepository transactions,
                              CategorizationService categorization) {
        this.statements = statements;
        this.transactions = transactions;
        this.categorization = categorization;
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUploaded(StatementUploaded event) {
        process(event.statementId());
    }

    void process(Long statementId) {
        Statement statement = statements.findById(statementId).orElse(null);
        if (statement == null) {
            return;
        }
        try {
            List<StatementTransaction> items = transactions.findByStatementIdOrderByDateTimeDesc(statementId);
            List<TransactionInput> inputs = IntStream.range(0, items.size())
                    .mapToObj(i -> new TransactionInput(i, items.get(i).getDescription(), items.get(i).getMcc(),
                            items.get(i).getAmount()))
                    .toList();

            int ai = 0;
            for (var result : categorization.categorize(inputs)) {
                items.get(result.index()).categorize(result.category(), result.source());
                if (result.source() == CategorySource.AI) ai++;
            }
            statement.markDone(ai);
            log.info("Statement {} processed: {} transactions, {} by AI", statementId, items.size(), ai);
        } catch (RuntimeException e) {
            log.error("Statement {} failed", statementId, e);
            statement.markFailed(e.getMessage());
        }
    }
}
