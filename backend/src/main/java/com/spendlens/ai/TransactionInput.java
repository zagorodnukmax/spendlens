package com.spendlens.ai;

import java.math.BigDecimal;

/** What the categorizers see about one transaction. {@code index} links the answer back to the row. */
public record TransactionInput(int index, String description, Integer mcc, BigDecimal amount) {
}
