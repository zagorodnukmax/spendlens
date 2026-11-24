package com.spendlens.audit;

import com.spendlens.statement.Category;
import com.spendlens.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Audit endpoints with a fake agent in the Spring context: start → live steps → report. */
@Import(AuditApiTest.FakeAgentConfig.class)
class AuditApiTest extends ApiTestBase {

    @TestConfiguration
    static class FakeAgentConfig {
        @Bean
        AuditAgent fakeAuditAgent() {
            return (transactions, onStep) -> {
                onStep.accept(new AuditStepInfo("get_overview", "", "overview"));
                onStep.accept(new AuditStepInfo("search_transactions", "{\"category\":\"OTHER\"}", "1 matches"));
                var mystery = transactions.stream().filter(t -> t.getDescription().equals("Uklon trip")).findFirst()
                        .orElseThrow();
                var report = new AuditReport("One taxi ride is uncategorized.", List.of(new AuditReport.Finding(
                        AuditReport.FindingType.MISCATEGORIZED, AuditReport.Severity.MEDIUM, "Taxi in Other",
                        "Uklon is a taxi service.", List.of(mystery.getId()), Category.TRANSPORT)), true);
                onStep.accept(new AuditStepInfo("submit_report", "", "1 findings"));
                return new AuditAgent.Result(report, 4200, 300);
            };
        }
    }

    private static final String CSV = "date,description,amount\n2026-11-01,Coffee,-50\n2026-11-02,Uklon trip,-180\n";

    private org.springframework.test.web.servlet.assertj.MvcTestResult waitForAudit(String token, long id) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            var r = get("/api/statements/" + id + "/audits/latest", token);
            if (!"RUNNING".equals(read(r, "$.status"))) return r;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("audit did not finish");
    }

    @Test
    void auditRunsInBackgroundAndStoresStepsAndReport() {
        String token = registerUser();
        long id = uploadAndWait(token, CSV);

        assertThat(get("/api/statements/" + id + "/audits/latest", token)).hasStatus(404); // none yet

        var started = postJson("/api/statements/" + id + "/audits", token, "");
        assertThat(started).hasStatus(202);
        assertThat(started).bodyJson().extractingPath("$.status").isEqualTo("RUNNING");

        var done = waitForAudit(token, id);
        assertThat(done).bodyJson().extractingPath("$.status").isEqualTo("DONE");
        assertThat(done).bodyJson().extractingPath("$.steps[*].tool").asArray()
                .containsExactly("get_overview", "search_transactions", "submit_report");
        assertThat(done).bodyJson().extractingPath("$.report.summary").isEqualTo("One taxi ride is uncategorized.");
        assertThat(done).bodyJson().extractingPath("$.report.findings[0].suggestedCategory").isEqualTo("TRANSPORT");
        assertThat(done).bodyJson().extractingPath("$.inputTokens").isEqualTo(4200);

        // the user applies the suggestion through the normal endpoint (human in the loop)
        int txId = read(done, "$.report.findings[0].transactionIds[0]");
        var applied = mvc.patch().uri("/api/statements/" + id + "/transactions/" + txId)
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"category\":\"TRANSPORT\"}").exchange();
        assertThat(applied).bodyJson().extractingPath("$.categorySource").isEqualTo("MANUAL");
    }

    @Test
    void auditsAreScopedToTheOwnerAndDeletedWithTheStatement() {
        String alice = registerUser();
        String bob = registerUser();
        long id = uploadAndWait(alice, CSV);
        postJson("/api/statements/" + id + "/audits", alice, "");
        waitForAudit(alice, id);

        assertThat(postJson("/api/statements/" + id + "/audits", bob, "")).hasStatus(404);
        assertThat(get("/api/statements/" + id + "/audits/latest", bob)).hasStatus(404);

        assertThat(mvc.delete().uri("/api/statements/" + id).header("Authorization", "Bearer " + alice))
                .hasStatus(204);
    }
}
