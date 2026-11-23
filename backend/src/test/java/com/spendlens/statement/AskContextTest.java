package com.spendlens.statement;

import com.spendlens.ai.CategoryAssignment;
import com.spendlens.ai.ClaudeGateway;
import com.spendlens.ai.TransactionInput;
import com.spendlens.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** With a fake Claude gateway in the context: AI categorization and Q&A work end to end over HTTP. */
@Import(AskContextTest.FakeClaudeConfig.class)
class AskContextTest extends IntegrationTest {

    static final AtomicReference<String> lastContext = new AtomicReference<>();

    @TestConfiguration
    static class FakeClaudeConfig {
        @Bean
        @Primary
        ClaudeGateway fakeClaude() {
            return new ClaudeGateway() {
                @Override
                public List<CategoryAssignment> categorize(List<TransactionInput> batch) {
                    return batch.stream().map(t -> new CategoryAssignment(t.index(), Category.RESTAURANTS)).toList();
                }

                @Override
                public String answer(String question, String context) {
                    lastContext.set(context);
                    return "You spent 135 UAH on coffee.";
                }
            };
        }
    }

    @Test
    void aiCategorizesAndAnswersWithStatementContext() {
        String token = registerUser();
        long id = uploadAndWait(token, """
                date,description,amount
                2026-11-01,Aroma Kava,-50
                2026-11-02,Aroma Kava,-85
                """);

        assertThat(get("/api/statements/" + id, token)).bodyJson().extractingPath("$.aiCategorized").isEqualTo(2);
        assertThat(get("/api/statements/" + id + "/transactions", token))
                .bodyJson().extractingPath("$[0].categorySource").isEqualTo("AI");

        var ask = postJson("/api/statements/" + id + "/ask", token, "{\"question\":\"How much on coffee?\"}");
        assertThat(ask).hasStatusOk();
        assertThat(ask).bodyJson().extractingPath("$.answer").isEqualTo("You spent 135 UAH on coffee.");
        assertThat(lastContext.get())
                .contains("Total expenses: 135.00 UAH")
                .contains("2026-11-02 | RESTAURANTS | -85.00 | Aroma Kava");
    }

    @Test
    void emptyQuestionIsRejected() {
        String token = registerUser();
        long id = uploadAndWait(token, "date,description,amount\n2026-11-01,Coffee,-50\n");
        assertThat(postJson("/api/statements/" + id + "/ask", token, "{\"question\":\" \"}")).hasStatus(400);
    }
}
