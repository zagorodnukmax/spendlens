package com.spendlens.statement;

import com.spendlens.ai.ClaudeGateway;
import com.spendlens.common.AiUnavailableException;
import com.spendlens.common.BadRequestException;
import com.spendlens.common.ConflictException;
import com.spendlens.common.NotFoundException;
import com.spendlens.config.AppProperties;
import com.spendlens.insights.Insights;
import com.spendlens.insights.InsightsService;
import com.spendlens.statement.StatementDtos.*;
import com.spendlens.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StatementService {

    private final StatementRepository statements;
    private final StatementTransactionRepository transactions;
    private final UserRepository users;
    private final StatementParser parser;
    private final InsightsService insightsService;
    private final ApplicationEventPublisher events;
    private final Optional<ClaudeGateway> claude;
    private final AppProperties props;

    public StatementService(StatementRepository statements, StatementTransactionRepository transactions,
                            UserRepository users, StatementParser parser, InsightsService insightsService,
                            ApplicationEventPublisher events, Optional<ClaudeGateway> claude, AppProperties props) {
        this.statements = statements;
        this.transactions = transactions;
        this.users = users;
        this.parser = parser;
        this.insightsService = insightsService;
        this.events = events;
        this.claude = claude;
        this.props = props;
    }

    /** Parses and stores the file, then hands it to {@link StatementProcessor} for background categorization. */
    @Transactional
    public StatementResponse upload(Long userId, String fileName, String content) {
        var parsed = parser.parse(content);
        var dates = parsed.transactions().stream().map(ParsedTransaction::dateTime).toList();
        var statement = statements.save(new Statement(
                users.getReferenceById(userId),
                fileName == null || fileName.isBlank() ? "statement.csv" : fileName,
                parsed.format(),
                parsed.transactions().size(),
                dates.stream().min(Comparator.naturalOrder()).map(LocalDateTime::toLocalDate).orElse(null),
                dates.stream().max(Comparator.naturalOrder()).map(LocalDateTime::toLocalDate).orElse(null)));
        transactions.saveAll(parsed.transactions().stream()
                .map(p -> new StatementTransaction(statement, p))
                .toList());
        events.publishEvent(new StatementUploaded(statement.getId()));
        return StatementResponse.from(statement);
    }

    @Transactional(readOnly = true)
    public List<StatementResponse> list(Long userId) {
        return statements.findByOwnerIdOrderByUploadedAtDesc(userId).stream().map(StatementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StatementResponse get(Long userId, Long id) {
        return StatementResponse.from(find(userId, id));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> transactions(Long userId, Long id) {
        find(userId, id);
        return transactions.findByStatementIdOrderByDateTimeDesc(id).stream().map(TransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Insights insights(Long userId, Long id) {
        var statement = requireDone(find(userId, id));
        return insightsService.compute(transactions.findByStatementIdOrderByDateTimeDesc(statement.getId()));
    }

    @Transactional
    public TransactionResponse recategorize(Long userId, Long statementId, Long txId, Category category) {
        find(userId, statementId);
        var tx = transactions.findById(txId)
                .filter(t -> t.getStatement().getId().equals(statementId))
                .orElseThrow(() -> new NotFoundException("Transaction %d not found".formatted(txId)));
        tx.categorize(category, CategorySource.MANUAL);
        return TransactionResponse.from(tx);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        var statement = find(userId, id);
        transactions.deleteByStatementId(statement.getId());
        statements.delete(statement);
    }

    @Transactional(readOnly = true)
    public AskResponse ask(Long userId, Long id, String question) {
        var gateway = claude.orElseThrow(() -> new AiUnavailableException(
                "AI is not configured. Set the ANTHROPIC_API_KEY environment variable and restart the backend."));
        var statement = requireDone(find(userId, id));
        var items = transactions.findByStatementIdOrderByDateTimeDesc(statement.getId());
        String answer = gateway.answer(question.strip(), buildContext(statement, items));
        return new AskResponse(question.strip(), answer, props.ai().model());
    }

    public AiStatus aiStatus() {
        return new AiStatus(claude.isPresent(), claude.isPresent() ? props.ai().model() : null);
    }

    /** Compact, model-friendly view of the statement: precomputed totals plus every transaction. */
    String buildContext(Statement statement, List<StatementTransaction> items) {
        var insights = insightsService.compute(items);
        var sb = new StringBuilder();
        sb.append("Period: ").append(statement.getPeriodFrom()).append(" to ").append(statement.getPeriodTo()).append('\n');
        sb.append("Total income: ").append(insights.income()).append(" UAH\n");
        sb.append("Total expenses: ").append(insights.expenses()).append(" UAH\n");
        sb.append("Expenses by category: ").append(insights.byCategory().stream()
                .map(c -> c.label() + " " + c.total())
                .collect(Collectors.joining("; "))).append('\n');
        sb.append("\nTransactions (date | category | amount UAH | description):\n");
        for (var t : items) {
            sb.append(t.getDateTime().toLocalDate()).append(" | ")
                    .append(t.getCategory() == null ? "OTHER" : t.getCategory().name()).append(" | ")
                    .append(t.getAmount().toPlainString()).append(" | ")
                    .append(t.getDescription()).append('\n');
        }
        return sb.toString();
    }

    private Statement find(Long userId, Long id) {
        return statements.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new NotFoundException("Statement %d not found".formatted(id)));
    }

    private static Statement requireDone(Statement s) {
        return switch (s.getStatus()) {
            case DONE -> s;
            case PROCESSING -> throw new ConflictException("Statement is still being processed");
            case FAILED -> throw new BadRequestException("Statement processing failed: " + s.getError());
        };
    }
}
