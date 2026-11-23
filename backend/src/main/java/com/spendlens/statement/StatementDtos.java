package com.spendlens.statement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class StatementDtos {

    private StatementDtos() {
    }

    public record StatementResponse(Long id, String fileName, String format, StatementStatus status, String error,
                                    int transactionCount, int aiCategorized, LocalDate periodFrom, LocalDate periodTo,
                                    Instant uploadedAt, Instant processedAt) {
        static StatementResponse from(Statement s) {
            return new StatementResponse(s.getId(), s.getFileName(), s.getFormat(), s.getStatus(), s.getError(),
                    s.getTransactionCount(), s.getAiCategorized(), s.getPeriodFrom(), s.getPeriodTo(),
                    s.getUploadedAt(), s.getProcessedAt());
        }
    }

    public record TransactionResponse(Long id, LocalDateTime dateTime, String description, Integer mcc,
                                      BigDecimal amount, Category category, String categoryLabel,
                                      String categoryColor, CategorySource categorySource) {
        static TransactionResponse from(StatementTransaction t) {
            Category c = t.getCategory();
            return new TransactionResponse(t.getId(), t.getDateTime(), t.getDescription(), t.getMcc(), t.getAmount(),
                    c, c == null ? null : c.label(), c == null ? null : c.color(), t.getCategorySource());
        }
    }

    public record RecategorizeRequest(@NotNull Category category) {
    }

    public record AskRequest(@NotBlank @Size(max = 500) String question) {
    }

    public record AskResponse(String question, String answer, String model) {
    }

    public record AiStatus(boolean enabled, String model) {
    }
}
