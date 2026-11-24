package com.spendlens.audit;

import jakarta.persistence.*;

import java.time.Instant;

/** One tool call of an audit run; saved as it happens so the UI can show progress live. */
@Entity
@Table(name = "audit_steps", indexes = @Index(columnList = "run_id"))
public class AuditStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id")
    private AuditRun run;

    @Column(nullable = false, length = 60)
    private String tool;

    @Column(length = 1000)
    private String input;

    @Column(length = 300)
    private String outcome;

    @Column(nullable = false)
    private Instant at = Instant.now();

    protected AuditStep() {
    }

    public AuditStep(AuditRun run, AuditStepInfo info) {
        this.run = run;
        this.tool = info.tool();
        this.input = truncate(info.input(), 1000);
        this.outcome = truncate(info.outcome(), 300);
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() { return id; }
    public String getTool() { return tool; }
    public String getInput() { return input; }
    public String getOutcome() { return outcome; }
    public Instant getAt() { return at; }
}
