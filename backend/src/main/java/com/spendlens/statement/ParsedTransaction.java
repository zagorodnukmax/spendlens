package com.spendlens.statement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A transaction as read from the file, before it is stored or categorized. */
public record ParsedTransaction(LocalDateTime dateTime, String description, Integer mcc, BigDecimal amount) {
}
