package com.spendlens.audit;

import com.spendlens.statement.Statement;
import jakarta.persistence.*;

import java.time.Instant;

/** One execution of the audit agent for a statement. */
@Entity
@Table(name = "audit_runs", indexes = @Index(columnList = "statement_id"))
public class AuditRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id")
    private Statement statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditStatus status = AuditStatus.RUNNING;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant finishedAt;

    @Column(length = 500)
    private String error;

    /** The final report as JSON (see {@link AuditReport}). */
    @Lob
    private String reportJson;

    private long inputTokens;
    private long outputTokens;

    protected AuditRun() {
    }

    public AuditRun(Statement statement) {
        this.statement = statement;
    }

    public void finish(String reportJson, long inputTokens, long outputTokens) {
        this.status = AuditStatus.DONE;
        this.reportJson = reportJson;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.finishedAt = Instant.now();
    }

    public void fail(String error) {
        this.status = AuditStatus.FAILED;
        this.error = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 500));
        this.finishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Statement getStatement() { return statement; }
    public AuditStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getError() { return error; }
    public String getReportJson() { return reportJson; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
}
