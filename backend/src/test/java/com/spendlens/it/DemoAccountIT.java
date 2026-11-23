package com.spendlens.it;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The demo account from the README exists and its sample statement is fully analyzed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:it-demo;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=true",
        "app.ai.api-key="
})
class DemoAccountIT extends StatementItSupport {

    @Test
    void documentedDemoCredentialsWork() {
        String token = login("demo@spendlens.dev", "demo12345");
        assertThat((String) get("/api/me", token).json("$.email")).isEqualTo("demo@spendlens.dev");
    }

    @Test
    void sampleStatementIsPreloadedAndAnalyzed() {
        String token = login("demo@spendlens.dev", "demo12345");

        List<String> files = get("/api/statements", token).json("$[*].fileName");
        assertThat(files).containsExactly("monobank-sample.csv");
        int id = get("/api/statements", token).json("$[0].id");

        var statement = waitUntilProcessed(token, id);
        assertThat((String) statement.json("$.status")).isEqualTo("DONE");
        assertThat((Integer) statement.json("$.transactionCount")).isEqualTo(55);

        var insights = get("/api/statements/" + id + "/insights", token);
        List<String> subscriptions = insights.json("$.subscriptions[*].merchant");
        assertThat(subscriptions).containsExactlyInAnyOrder("OpenAI ChatGPT", "Netflix.com", "Київстар", "Spotify P2A");
        assertThat((String) insights.json("$.anomalies[0].description")).isEqualTo("Сільпо");
    }
}
