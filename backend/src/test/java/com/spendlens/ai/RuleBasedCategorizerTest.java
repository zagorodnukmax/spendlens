package com.spendlens.ai;

import com.spendlens.statement.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedCategorizerTest {

    private final RuleBasedCategorizer rules = new RuleBasedCategorizer();

    @ParameterizedTest(name = "{0} / mcc {1} / {2} -> {3}")
    @CsvSource(nullValues = "null", value = {
            "Зарплата ТОВ Техносфера, 4829, 42000, INCOME",
            "Сільпо,                 5411, -600,  GROCERIES",
            "OpenAI ChatGPT,         5734, -860,  SUBSCRIPTIONS",  // keyword beats the software-store MCC
            "Some unknown shop,      5411, -100,  GROCERIES",      // MCC fallback
            "Bolt,                   null, -184,  TRANSPORT",
            "Переказ на картку,      4829, -1500, TRANSFERS",
            "Банкомат ПриватБанк,    6011, -2000, CASH",
            "Hotel Lviv,             3501, -3000, TRAVEL",          // airline/hotel MCC range
            "Random store,           5651, -900,  SHOPPING",       // retail MCC range
            "Mystery,                null, -50,   OTHER",
            "Mystery,                9999, -50,   OTHER"
    })
    void categorizes(String description, Integer mcc, String amount, Category expected) {
        var input = new TransactionInput(0, description, mcc, new BigDecimal(amount));
        assertThat(rules.categorize(input)).isEqualTo(expected);
    }

    @Test
    void keywordsMatchOnlyAtWordStart() {
        // Regression: "ПриватБанк" contains "атб" (a supermarket chain) in the middle of the word.
        assertThat(RuleBasedCategorizer.containsWord("банкомат приватбанк", "атб")).isFalse();
        assertThat(RuleBasedCategorizer.containsWord("магазин атб №5", "атб")).isTrue();
        assertThat(RuleBasedCategorizer.containsWord("atb", "atb")).isTrue();
    }
}
