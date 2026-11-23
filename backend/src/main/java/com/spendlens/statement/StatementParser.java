package com.spendlens.statement;

import com.spendlens.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a bank statement CSV. Two layouts are supported:
 * <ul>
 *   <li><b>MONOBANK</b> — the CSV exported from the monobank app
 *       ("Дата i час операції", "Деталі операції", "MCC", "Сума в валюті картки (UAH)", ...);</li>
 *   <li><b>GENERIC</b> — any CSV with the columns {@code date, description, amount} and an optional {@code mcc}.</li>
 * </ul>
 * The separator (comma or semicolon) and decimal comma are detected automatically.
 */
@Component
public class StatementParser {

    public static final int MAX_ROWS = 5000;

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"));

    public record Result(String format, List<ParsedTransaction> transactions) {
    }

    private record Layout(String format, int date, int description, int amount, int mcc) {
    }

    public Result parse(String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("The file is empty");
        }
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        List<String> lines = text.lines().filter(l -> !l.isBlank()).toList();
        if (lines.size() < 2) {
            throw new BadRequestException("The file has no transactions");
        }
        if (lines.size() - 1 > MAX_ROWS) {
            throw new BadRequestException("Too many rows: max " + MAX_ROWS);
        }

        char separator = detectSeparator(lines.getFirst());
        Layout layout = detectLayout(splitCsvLine(lines.getFirst(), separator));

        var result = new ArrayList<ParsedTransaction>();
        for (int i = 1; i < lines.size(); i++) {
            int lineNo = i + 1;
            List<String> cells = splitCsvLine(lines.get(i), separator);
            try {
                result.add(new ParsedTransaction(
                        parseDateTime(cell(cells, layout.date())),
                        normalizeDescription(cell(cells, layout.description())),
                        layout.mcc() < 0 ? null : parseMcc(cell(cells, layout.mcc())),
                        parseAmount(cell(cells, layout.amount()))));
            } catch (RuntimeException e) {
                throw new BadRequestException("Line %d: %s".formatted(lineNo, e.getMessage()));
            }
        }
        return new Result(layout.format(), result);
    }

    // ---- layout detection ----

    private static Layout detectLayout(List<String> header) {
        List<String> h = header.stream().map(s -> s.strip().toLowerCase(Locale.ROOT)).toList();

        int mcc = indexOf(h, "mcc");
        if (!h.isEmpty() && h.getFirst().startsWith("дата") && mcc >= 0) {
            // monobank: date, details, MCC, amount in card currency, ...
            int amount = indexWhere(h, s -> s.startsWith("сума в валюті картки"));
            return new Layout("MONOBANK", 0, 1, amount >= 0 ? amount : 3, mcc);
        }

        int date = indexOf(h, "date");
        int description = indexOf(h, "description");
        int amount = indexOf(h, "amount");
        if (date < 0 || description < 0 || amount < 0) {
            throw new BadRequestException(
                    "Unknown CSV format. Use a monobank export or a CSV with columns: date, description, amount[, mcc]");
        }
        return new Layout("GENERIC", date, description, amount, mcc);
    }

    private static int indexOf(List<String> header, String name) {
        return header.indexOf(name);
    }

    private static int indexWhere(List<String> header, java.util.function.Predicate<String> p) {
        for (int i = 0; i < header.size(); i++) {
            if (p.test(header.get(i))) return i;
        }
        return -1;
    }

    private static char detectSeparator(String headerLine) {
        long commas = headerLine.chars().filter(c -> c == ',').count();
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        return semicolons > commas ? ';' : ',';
    }

    // ---- CSV ----

    /** Splits one CSV line, honouring double quotes and escaped quotes (""). */
    static List<String> splitCsvLine(String line, char separator) {
        var cells = new ArrayList<String>();
        var current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == separator) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static String cell(List<String> cells, int index) {
        if (index >= cells.size()) {
            throw new IllegalArgumentException("missing column " + (index + 1));
        }
        return cells.get(index).strip();
    }

    // ---- values ----

    static LocalDateTime parseDateTime(String value) {
        for (var f : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(value, f);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        for (var f : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, f).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IllegalArgumentException("invalid date '" + value + "'");
    }

    static BigDecimal parseAmount(String value) {
        String v = value.replace(" ", "").replace(" ", "");
        if (v.contains(",") && !v.contains(".")) {
            v = v.replace(',', '.');
        } else {
            v = v.replace(",", "");
        }
        try {
            BigDecimal amount = new BigDecimal(v);
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("amount is zero");
            }
            return amount.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount '" + value + "'");
        }
    }

    private static Integer parseMcc(String value) {
        if (value.isEmpty()) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid MCC '" + value + "'");
        }
    }

    private static String normalizeDescription(String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("empty description");
        }
        String v = value.replaceAll("\\s+", " ");
        return v.length() > 300 ? v.substring(0, 300) : v;
    }
}
