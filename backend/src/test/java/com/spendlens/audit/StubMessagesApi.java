package com.spendlens.audit;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A local fake of POST /v1/messages that replays scripted assistant turns and records every request.
 * Lets the real Anthropic SDK (and its Tool Runner loop) run in tests without a key or network.
 */
class StubMessagesApi implements AutoCloseable {

    final List<String> requests = new ArrayList<>();
    private final Deque<String> responses = new ArrayDeque<>();
    private final HttpServer server;

    StubMessagesApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            synchronized (this) {
                requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            String body = responses.isEmpty() ? endTurn("(no more scripted turns)") : responses.poll();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    StubMessagesApi then(String response) {
        responses.add(response);
        return this;
    }

    static String toolUse(String id, String name, String inputJson) {
        return message("[{\"type\":\"tool_use\",\"id\":\"%s\",\"name\":\"%s\",\"input\":%s}]"
                .formatted(id, name, inputJson), "tool_use");
    }

    static String endTurn(String text) {
        return message("[{\"type\":\"text\",\"text\":\"%s\"}]".formatted(text), "end_turn");
    }

    static String message(String contentJson, String stopReason) {
        return """
                {"id":"msg_%s","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":%s,"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":1000,"output_tokens":50}}
                """.formatted(Long.toHexString(System.nanoTime()), contentJson, stopReason);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
