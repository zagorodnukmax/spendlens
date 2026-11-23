package com.spendlens.statement;

import com.spendlens.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StatementApiTest extends ApiTestBase {

    private static String sample() {
        try {
            return new ClassPathResource("samples/monobank-sample.csv").getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void uploadIsProcessedInBackgroundAndProducesInsights() {
        String token = registerUser();
        long id = uploadAndWait(token, sample());

        var statement = get("/api/statements/" + id, token);
        assertThat(statement).bodyJson().extractingPath("$.format").isEqualTo("MONOBANK");
        assertThat(statement).bodyJson().extractingPath("$.transactionCount").isEqualTo(55);
        assertThat(statement).bodyJson().extractingPath("$.periodFrom").isEqualTo("2026-09-01");
        assertThat(statement).bodyJson().extractingPath("$.aiCategorized").isEqualTo(0); // AI disabled in tests

        var insights = get("/api/statements/" + id + "/insights", token);
        assertThat(insights).hasStatusOk();
        assertThat(insights).bodyJson().extractingPath("$.income").isEqualTo(126800.0);
        assertThat(insights).bodyJson().extractingPath("$.byMonth.length()").isEqualTo(3);
        assertThat(insights).bodyJson().extractingPath("$.subscriptions[*].merchant").asArray()
                .contains("Netflix.com", "Spotify P2A", "OpenAI ChatGPT", "Київстар");
        assertThat(insights).bodyJson().extractingPath("$.anomalies[0].description").isEqualTo("Сільпо");

        var txs = get("/api/statements/" + id + "/transactions", token);
        assertThat(txs).bodyJson().extractingPath("$.length()").isEqualTo(55);
        assertThat(txs).bodyJson().extractingPath("$[0].description").isEqualTo("Сільпо"); // newest first
        assertThat(txs).bodyJson().extractingPath("$[0].categorySource").isEqualTo("RULES");
    }

    @Test
    void invalidFileIsRejectedWithLineNumber() {
        String token = registerUser();
        var result = upload(token, "bad.csv", "date,description,amount\n2026-11-01,Coffee,abc\n");
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("Line 2", "invalid amount");
    }

    @Test
    void userCanCorrectACategory() {
        String token = registerUser();
        long id = uploadAndWait(token, "date,description,amount\n2026-11-01,Mystery shop,-200\n");
        int txId = read(get("/api/statements/" + id + "/transactions", token), "$[0].id");

        var patched = mvc.patch().uri("/api/statements/" + id + "/transactions/" + txId)
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"category\":\"ENTERTAINMENT\"}").exchange();

        assertThat(patched).hasStatusOk();
        assertThat(patched).bodyJson().extractingPath("$.category").isEqualTo("ENTERTAINMENT");
        assertThat(patched).bodyJson().extractingPath("$.categorySource").isEqualTo("MANUAL");
    }

    @Test
    void usersCannotSeeEachOthersStatements() {
        String alice = registerUser();
        String bob = registerUser();
        long id = uploadAndWait(alice, "date,description,amount\n2026-11-01,Coffee,-50\n");

        assertThat(get("/api/statements", bob)).bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(get("/api/statements/" + id, bob)).hasStatus(404);
        assertThat(get("/api/statements/" + id + "/transactions", bob)).hasStatus(404);
        assertThat(get("/api/statements/" + id + "/insights", bob)).hasStatus(404);
        assertThat(mvc.delete().uri("/api/statements/" + id).header("Authorization", "Bearer " + bob))
                .hasStatus(404);
    }

    @Test
    void deleteRemovesStatement() {
        String token = registerUser();
        long id = uploadAndWait(token, "date,description,amount\n2026-11-01,Coffee,-50\n");
        assertThat(mvc.delete().uri("/api/statements/" + id).header("Authorization", "Bearer " + token))
                .hasStatus(204);
        assertThat(get("/api/statements/" + id, token)).hasStatus(404);
    }

    @Test
    void askReturns503WhenAiIsNotConfigured() {
        String token = registerUser();
        long id = uploadAndWait(token, "date,description,amount\n2026-11-01,Coffee,-50\n");

        assertThat(get("/api/ai/status", token)).bodyJson().extractingPath("$.enabled").isEqualTo(false);
        var ask = postJson("/api/statements/" + id + "/ask", token, "{\"question\":\"How much on coffee?\"}");
        assertThat(ask).hasStatus(503);
        assertThat(ask).bodyJson().extractingPath("$.detail").asString().contains("ANTHROPIC_API_KEY");
    }

    @Test
    void endpointsRequireBearerToken() {
        assertThat(get("/api/statements", null)).hasStatus(401);
        assertThat(get("/api/statements", "garbage")).hasStatus(401);
    }
}
