package com.spendlens.it;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A whole user session over real HTTP, with AI switched off (rules mode). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:it-flow;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false",
        "app.ai.api-key="
})
class StatementFlowIT extends StatementItSupport {

    private static final String CSV = """
            date,description,amount,mcc
            2026-11-01,Salary,30000,
            2026-11-02,Сільпо,-640.50,5411
            2026-11-03,Netflix,-299,4899
            2026-11-05,"Bolt ride, airport",-310,4121
            2026-11-07,Mystery shop,-85,
            """;

    @Test
    void uploadAnalyzeCorrectAndDelete() {
        String token = registerUser();

        // 1. Upload returns immediately with PROCESSING, then becomes DONE
        var created = upload(token, "november.csv", CSV);
        assertThat(created.status()).isEqualTo(201);
        assertThat((String) created.json("$.status")).isEqualTo("PROCESSING");
        int id = created.json("$.id");
        var done = waitUntilProcessed(token, id);
        assertThat((String) done.json("$.status")).isEqualTo("DONE");
        assertThat((String) done.json("$.format")).isEqualTo("GENERIC");

        // 2. Rules categorized everything
        var txs = get("/api/statements/" + id + "/transactions", token);
        List<String> categories = txs.json("$[*].category");
        assertThat(categories).containsExactly("OTHER", "TRANSPORT", "SUBSCRIPTIONS", "GROCERIES", "INCOME");
        List<String> sources = txs.json("$[*].categorySource");
        assertThat(sources).containsOnly("RULES");

        // 3. Insights are exact
        var insights = get("/api/statements/" + id + "/insights", token);
        assertThat(insights.body()).contains("\"income\":30000.00", "\"expenses\":1334.50", "\"balance\":28665.50");

        // 4. User corrects a category; insights follow
        int mysteryId = txs.json("$[0].id");
        var patched = json("PATCH", "/api/statements/" + id + "/transactions/" + mysteryId, token,
                "{\"category\":\"ENTERTAINMENT\"}");
        assertThat((String) patched.json("$.categorySource")).isEqualTo("MANUAL");
        List<String> after = get("/api/statements/" + id + "/insights", token).json("$.byCategory[*].category");
        assertThat(after).contains("ENTERTAINMENT").doesNotContain("OTHER");

        // 5. Ask needs AI → clear 503 in rules mode
        var ask = json("POST", "/api/statements/" + id + "/ask", token, "{\"question\":\"How much on food?\"}");
        assertThat(ask.status()).isEqualTo(503);
        assertThat(ask.body()).contains("ANTHROPIC_API_KEY");

        // 6. Delete
        assertThat(delete("/api/statements/" + id, token).status()).isEqualTo(204);
        assertThat(get("/api/statements/" + id, token).status()).isEqualTo(404);
    }

    @Test
    void badFilesAreRejectedWithUsefulMessages() {
        String token = registerUser();

        var badRow = upload(token, "bad.csv", "date,description,amount\n2026-11-01,Coffee,abc\n");
        assertThat(badRow.status()).isEqualTo(400);
        assertThat(badRow.body()).contains("Line 2", "invalid amount");

        var unknown = upload(token, "unknown.csv", "foo,bar\n1,2\n");
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.body()).contains("Unknown CSV format");

        byte[] huge = new byte[6 * 1024 * 1024];
        Arrays.fill(huge, (byte) 'a');
        var tooLarge = upload(token, "huge.csv", huge);
        assertThat(tooLarge.status()).isEqualTo(413);
    }

    @Test
    void statementsAreIsolatedBetweenUsers() {
        String alice = registerUser();
        String bob = registerUser();
        int id = uploadAndWait(alice, CSV);

        assertThat(get("/api/statements", bob).body()).isEqualTo("[]");
        assertThat(get("/api/statements/" + id + "/insights", bob).status()).isEqualTo(404);
        assertThat(delete("/api/statements/" + id, bob).status()).isEqualTo(404);
        assertThat(get("/api/statements/" + id, alice).status()).isEqualTo(200);
    }
}
