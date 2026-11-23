package com.spendlens.statement;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A single line of a statement. Negative amount = expense, positive = income. */
@Entity
@Table(name = "statement_transactions", indexes = @Index(columnList = "statement_id"))
public class StatementTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id")
    private Statement statement;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Column(nullable = false, length = 300)
    private String description;

    /** Merchant Category Code from the bank (ISO 18245), if present in the export. */
    private Integer mcc;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private CategorySource categorySource;

    protected StatementTransaction() {
    }

    public StatementTransaction(Statement statement, ParsedTransaction parsed) {
        this.statement = statement;
        this.dateTime = parsed.dateTime();
        this.description = parsed.description();
        this.mcc = parsed.mcc();
        this.amount = parsed.amount();
    }

    public void categorize(Category category, CategorySource source) {
        this.category = category;
        this.categorySource = source;
    }

    public boolean isExpense() {
        return amount.signum() < 0;
    }

    public Long getId() { return id; }
    public Statement getStatement() { return statement; }
    public LocalDateTime getDateTime() { return dateTime; }
    public String getDescription() { return description; }
    public Integer getMcc() { return mcc; }
    public BigDecimal getAmount() { return amount; }
    public Category getCategory() { return category; }
    public CategorySource getCategorySource() { return categorySource; }
}
