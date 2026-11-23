package com.spendlens.statement;

import com.spendlens.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** One uploaded bank statement file. Its transactions are categorized asynchronously. */
@Entity
@Table(name = "statements")
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User owner;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 20)
    private String format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatementStatus status = StatementStatus.PROCESSING;

    @Column(length = 500)
    private String error;

    private int transactionCount;
    private int aiCategorized;
    private LocalDate periodFrom;
    private LocalDate periodTo;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    private Instant processedAt;

    protected Statement() {
    }

    public Statement(User owner, String fileName, String format, int transactionCount,
                     LocalDate periodFrom, LocalDate periodTo) {
        this.owner = owner;
        this.fileName = fileName;
        this.format = format;
        this.transactionCount = transactionCount;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
    }

    public void markDone(int aiCategorized) {
        this.status = StatementStatus.DONE;
        this.aiCategorized = aiCategorized;
        this.processedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = StatementStatus.FAILED;
        this.error = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 500));
        this.processedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getFileName() { return fileName; }
    public String getFormat() { return format; }
    public StatementStatus getStatus() { return status; }
    public String getError() { return error; }
    public int getTransactionCount() { return transactionCount; }
    public int getAiCategorized() { return aiCategorized; }
    public LocalDate getPeriodFrom() { return periodFrom; }
    public LocalDate getPeriodTo() { return periodTo; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
