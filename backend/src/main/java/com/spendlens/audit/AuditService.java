package com.spendlens.audit;

import com.spendlens.common.AiUnavailableException;
import com.spendlens.common.ConflictException;
import com.spendlens.common.NotFoundException;
import com.spendlens.statement.Statement;
import com.spendlens.statement.StatementRepository;
import com.spendlens.statement.StatementStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@Service
public class AuditService {

    private final Optional<AuditAgent> agent;
    private final StatementRepository statements;
    private final AuditRunRepository runs;
    private final AuditStepRepository steps;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;

    public AuditService(Optional<AuditAgent> agent, StatementRepository statements, AuditRunRepository runs,
                        AuditStepRepository steps, ApplicationEventPublisher events, JsonMapper json) {
        this.agent = agent;
        this.statements = statements;
        this.runs = runs;
        this.steps = steps;
        this.events = events;
        this.json = json;
    }

    @Transactional
    public AuditRunResponse start(Long userId, Long statementId) {
        Statement statement = find(userId, statementId);
        if (agent.isEmpty()) {
            throw new AiUnavailableException(
                    "AI is not configured. Set the ANTHROPIC_API_KEY environment variable and restart the backend.");
        }
        if (statement.getStatus() != StatementStatus.DONE) {
            throw new ConflictException("The statement must finish processing before it can be audited");
        }
        runs.findFirstByStatementIdOrderByIdDesc(statementId)
                .filter(r -> r.getStatus() == AuditStatus.RUNNING)
                .ifPresent(r -> {
                    throw new ConflictException("An audit is already running for this statement");
                });
        var run = runs.save(new AuditRun(statement));
        events.publishEvent(new AuditRequested(run.getId()));
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public AuditRunResponse latest(Long userId, Long statementId) {
        find(userId, statementId);
        return runs.findFirstByStatementIdOrderByIdDesc(statementId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("No audit for this statement yet"));
    }

    private AuditRunResponse toResponse(AuditRun run) {
        var stepList = run.getId() == null ? java.util.List.<AuditRunResponse.Step>of()
                : steps.findByRunIdOrderByIdAsc(run.getId()).stream()
                .map(s -> new AuditRunResponse.Step(s.getTool(), s.getInput(), s.getOutcome(), s.getAt()))
                .toList();
        AuditReport report = run.getReportJson() == null ? null
                : json.readValue(run.getReportJson(), AuditReport.class);
        return new AuditRunResponse(run.getId(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
                run.getError(), stepList, report, run.getInputTokens(), run.getOutputTokens());
    }

    private Statement find(Long userId, Long statementId) {
        return statements.findByIdAndOwnerId(statementId, userId)
                .orElseThrow(() -> new NotFoundException("Statement %d not found".formatted(statementId)));
    }
}
