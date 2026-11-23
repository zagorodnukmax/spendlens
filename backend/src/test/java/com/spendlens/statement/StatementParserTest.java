package com.spendlens.statement;

import com.spendlens.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementParserTest {

    private final StatementParser parser = new StatementParser();

    @Test
    void parsesMonobankExport() throws Exception {
        String csv = new ClassPathResource("samples/monobank-sample.csv").getContentAsString(StandardCharsets.UTF_8);

        var result = parser.parse(csv);

        assertThat(result.format()).isEqualTo("MONOBANK");
        assertThat(result.transactions()).hasSize(55);
        var first = result.transactions().getFirst();
        assertThat(first.dateTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 9, 0, 12));
        assertThat(first.description()).isEqualTo("Зарплата ТОВ Техносфера");
        assertThat(first.mcc()).isEqualTo(4829);
        assertThat(first.amount()).isEqualByComparingTo("42000");
        assertThat(result.transactions().get(1).amount()).isEqualByComparingTo("-95");
    }

    @Test
    void parsesGenericCsvWithQuotesAndOptionalMcc() {
        var result = parser.parse("date,description,amount,mcc\n"
                + "2026-11-05,\"Bolt ride, \"\"airport\"\"\",-310.50,4121\n"
                + "2026-11-06,Salary,30000,\n");

        assertThat(result.format()).isEqualTo("GENERIC");
        assertThat(result.transactions()).extracting(ParsedTransaction::description)
                .containsExactly("Bolt ride, \"airport\"", "Salary");
        assertThat(result.transactions().getFirst().mcc()).isEqualTo(4121);
        assertThat(result.transactions().get(1).mcc()).isNull();
    }

    @Test
    void supportsSemicolonsDecimalCommaAndBom() {
        var result = parser.parse("﻿date;description;amount\n05.11.2026;Кава;-85,50\n");
        var t = result.transactions().getFirst();
        assertThat(t.amount()).isEqualByComparingTo(new BigDecimal("-85.50"));
        assertThat(t.dateTime()).isEqualTo(LocalDateTime.of(2026, 11, 5, 0, 0));
    }

    @Test
    void reportsLineNumberOfABadRow() {
        assertThatThrownBy(() -> parser.parse("""
                date,description,amount
                2026-11-05,Coffee,-85
                not-a-date,Tea,-40
                """))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Line 3")
                .hasMessageContaining("invalid date");
    }

    @Test
    void rejectsUnknownFormatEmptyFileAndZeroAmount() {
        assertThatThrownBy(() -> parser.parse("foo,bar\n1,2\n")).hasMessageContaining("Unknown CSV format");
        assertThatThrownBy(() -> parser.parse("   ")).hasMessageContaining("empty");
        assertThatThrownBy(() -> parser.parse("date,description,amount\n")).hasMessageContaining("no transactions");
        assertThatThrownBy(() -> parser.parse("date,description,amount\n2026-11-05,X,0\n"))
                .hasMessageContaining("zero");
    }

    @Test
    void splitsCsvLinesCorrectly() {
        assertThat(StatementParser.splitCsvLine("a,\"b,c\",,\"d\"\"e\"", ','))
                .isEqualTo(List.of("a", "b,c", "", "d\"e"));
    }
}
