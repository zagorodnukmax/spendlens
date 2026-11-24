package com.spendlens.audit;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.jayway.jsonpath.JsonPath;
import com.spendlens.common.AiUnavailableException;
import com.spendlens.insights.InsightsService;
import com.spendlens.statement.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.spendlens.audit.StubMessagesApi.endTurn;
import static com.spendlens.audit.StubMessagesApi.message;
import static com.spendlens.audit.StubMessagesApi.toolUse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the real SDK Tool Runner through a scripted multi-step conversation against a local stub,
 * checking what we send at every turn and how the tool results flow back to the model.
 */
class ClaudeAuditAgentTest {

    private StubMessagesApi api;
    private ClaudeAuditAgent agent;
    private final List<AuditStepInfo> steps = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        api = new StubMessagesApi();
        var client = AnthropicOkHttpClient.builder().apiKey("test").baseUrl(api.baseUrl()).maxRetries(0).build();
        agent = new ClaudeAuditAgent(client, "claude-opus-5-5", new InsightsService());
    }

    @AfterEach
    void tearDown() {
        api.close();
    }

    private static final String REPORT = """
            {"summary":"Mostly fine; one taxi ride is uncategorized and Silpo charged twice.",
             "findings":[
               {"type":"MISCATEGORIZED","severity":"MEDIUM","title":"Taxi ride in Other",
                "details":"Uklon is a taxi service.","transaction_ids":[4,999],"suggested_category":"TRANSPORT"},
               {"type":"DUPLICATE","severity":"HIGH","title":"Silpo charged twice",
                "details":"Two identical charges of 640.50 a day apart.","transaction_ids":[5,6],"suggested_category":"GROCERIES"}
             ]}""";

    @Test
    void multiStepAuditEndsWithValidatedReport() {
        api.then(toolUse("t1", "get_overview", "{}"))
           .then(toolUse("t2", "search_transactions",
                   "{\"category\":\"OTHER\",\"text\":\"\",\"source\":null,\"type\":null,"
                   + "\"min_amount\":null,\"max_amount\":null,\"limit\":null}"))
           .then(message("""
                   [{"type":"tool_use","id":"t3","name":"find_possible_duplicates","input":{"max_days_apart":2}},
                    {"type":"tool_use","id":"t4","name":"list_recurring_charges","input":{}}]""", "tool_use"))
           .then(toolUse("t5", "submit_report", REPORT))
           .then(endTurn("Done."));

        var result = agent.run(AuditFixtures.statement(), steps::add);

        // the loop: 5 model turns, one request each
        assertThat(api.requests).hasSize(5);
        String first = api.requests.getFirst();
        List<String> toolNames = JsonPath.read(first, "$.tools[*].name");
        assertThat(toolNames).containsExactly("get_overview", "search_transactions", "find_possible_duplicates",
                "get_merchant_history", "list_recurring_charges", "submit_report");
        assertThat((String) JsonPath.read(first, "$.model")).isEqualTo("claude-opus-5-5");
        assertThat((String) JsonPath.read(first, "$.output_config.effort")).isEqualTo("medium");
        assertThat((String) JsonPath.read(first, "$.fallbacks")).isEqualTo("default");
        assertThat((String) JsonPath.read(first, "$.system")).contains("submit_report exactly once");

        // tool results go back to the model as real data
        String second = api.requests.get(1);
        List<String> overviewResult = JsonPath.read(second, "$.messages[-1].content[?(@.type=='tool_result')].content");
        assertThat(overviewResult.toString()).contains("\\\"transactions\\\":7").contains("\\\"income\\\":40000");
        // two parallel tool calls are answered in one user message
        List<?> parallelResults = JsonPath.read(api.requests.get(3), "$.messages[-1].content[?(@.type=='tool_result')]");
        assertThat(parallelResults).hasSize(2);

        // steps were reported as they happened
        assertThat(steps).extracting(AuditStepInfo::tool).containsExactly("get_overview", "search_transactions",
                "find_possible_duplicates", "list_recurring_charges", "submit_report");
        assertThat(steps.get(1).input()).isEqualTo("{\"category\":\"OTHER\"}");
        assertThat(steps.get(1).outcome()).isEqualTo("1 matches");
        assertThat(steps.get(2).outcome()).isEqualTo("1 groups");

        // report: made-up id 999 removed; suggested category kept only for MISCATEGORIZED
        var report = result.report();
        assertThat(report.complete()).isTrue();
        assertThat(report.findings()).hasSize(2);
        assertThat(report.findings().get(0).transactionIds()).containsExactly(4L);
        assertThat(report.findings().get(0).suggestedCategory()).isEqualTo(Category.TRANSPORT);
        assertThat(report.findings().get(1).suggestedCategory()).isNull();
        assertThat(result.inputTokens()).isEqualTo(5000);
        assertThat(result.outputTokens()).isEqualTo(250);
    }

    @Test
    void modelThatNeverSubmitsGivesIncompleteReportWithItsLastText() {
        api.then(toolUse("t1", "get_overview", "{}"))
           .then(endTurn("Everything looks normal."));

        var report = agent.run(AuditFixtures.statement(), steps::add).report();

        assertThat(report.complete()).isFalse();
        assertThat(report.summary()).isEqualTo("Everything looks normal.");
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void loopStopsAfterMaxIterations() {
        for (int i = 0; i < 30; i++) api.then(toolUse("t" + i, "get_overview", "{}"));

        var report = agent.run(AuditFixtures.statement(), steps::add).report();

        assertThat(api.requests).hasSize((int) ClaudeAuditAgent.MAX_ITERATIONS);
        assertThat(report.complete()).isFalse();
    }

    @Test
    void refusalStopsTheAudit() {
        api.then(message("[{\"type\":\"text\",\"text\":\"\"}]", "refusal"));
        assertThatThrownBy(() -> agent.run(AuditFixtures.statement(), steps::add))
                .isInstanceOf(AiUnavailableException.class);
    }
}
