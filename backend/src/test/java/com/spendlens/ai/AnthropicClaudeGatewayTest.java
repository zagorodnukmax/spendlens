package com.spendlens.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.jayway.jsonpath.JsonPath;
import com.spendlens.common.AiUnavailableException;
import com.spendlens.statement.Category;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Anthropic SDK client against a local fake of POST /v1/messages (JDK HttpServer).
 * Verifies what we send and how we parse the reply, without an API key or network access.
 */
class AnthropicClaudeGatewayTest {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> betaHeader = new AtomicReference<>();
    private volatile String responseJson;
    private AnthropicClaudeGateway gateway;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            betaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var client = AnthropicOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .maxRetries(0)
                .build();
        gateway = new AnthropicClaudeGateway(client, "claude-opus-5-5");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String message(String text, String stopReason) {
        return """
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":100,"output_tokens":20}}
                """.formatted(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .valueToTree(text).toString(), stopReason);
    }

    @Test
    void categorizeSendsStructuredRequestAndParsesItems() {
        responseJson = message("{\"items\":[{\"index\":0,\"category\":\"TRANSPORT\"},"
                + "{\"index\":1,\"category\":\"GROCERIES\"}]}", "end_turn");

        var result = gateway.categorize(List.of(
                new TransactionInput(0, "Bolt", 4121, new BigDecimal("-184.00")),
                new TransactionInput(1, "Сільпо", 5411, new BigDecimal("-612.40"))));

        assertThat(result).containsExactly(
                new CategoryAssignment(0, Category.TRANSPORT),
                new CategoryAssignment(1, Category.GROCERIES));

        String body = requestBody.get();
        assertThat((String) JsonPath.read(body, "$.model")).isEqualTo("claude-opus-5-5");
        assertThat((String) JsonPath.read(body, "$.output_config.effort")).isEqualTo("low");
        assertThat((String) JsonPath.read(body, "$.output_config.format.type")).isEqualTo("json_schema");
        List<String> categoryEnum = JsonPath.read(body, "$..category.enum[*]");
        assertThat(categoryEnum).contains("GROCERIES", "SUBSCRIPTIONS", "OTHER");
        assertThat((String) JsonPath.read(body, "$.fallbacks")).isEqualTo("default");
        assertThat(betaHeader.get()).contains("server-side-fallback-2026-07-01");
        String userText = JsonPath.read(body, "$.messages[0].content");
        assertThat(userText).contains("0 | 4121 | -184.00 | Bolt", "1 | 5411 | -612.40 | Сільпо");
    }

    @Test
    void answerReturnsTextAndSendsStatementContext() {
        responseJson = message("You spent 135 UAH on coffee.", "end_turn");

        String answer = gateway.answer("How much on coffee?", "Total expenses: 135.00 UAH");

        assertThat(answer).isEqualTo("You spent 135 UAH on coffee.");
        String body = requestBody.get();
        assertThat((String) JsonPath.read(body, "$.output_config.effort")).isEqualTo("medium");
        assertThat((String) JsonPath.read(body, "$.messages[0].content"))
                .contains("<statement>", "Total expenses: 135.00 UAH", "Question: How much on coffee?");
        Map<String, Object> system = Map.of("system", JsonPath.read(body, "$.system"));
        assertThat(system.get("system").toString()).contains("Reply in the language of the question");
    }

    @Test
    void refusalIsReportedAsAiUnavailable() {
        responseJson = message("", "refusal");
        assertThatThrownBy(() -> gateway.answer("?", "ctx")).isInstanceOf(AiUnavailableException.class);
    }
}
