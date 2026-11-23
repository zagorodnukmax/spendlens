package com.spendlens.it;

import com.spendlens.support.RealServerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Security, CORS, error format and the H2 console — only testable with a real server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:it-infra;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false",
        "app.ai.api-key="
})
class InfrastructureIT extends RealServerSupport {

    @Test
    void protectedEndpointsNeedAValidBearerToken() {
        assertThat(get("/api/statements", null).status()).isEqualTo(401);
        assertThat(get("/api/statements", null).header("WWW-Authenticate")).startsWith("Bearer");

        String token = registerUser();
        String forged = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";
        assertThat(get("/api/statements", forged).status()).isEqualTo(401);
        assertThat(get("/api/statements", token).status()).isEqualTo(200);
    }

    @Test
    void aiStatusReportsRulesMode() {
        String token = registerUser();
        assertThat(get("/api/ai/status", token).body()).contains("\"enabled\":false");
    }

    @Test
    void corsAllowsTheAngularDevServerOnly() {
        var allowed = send(HttpRequest.newBuilder(uri("/api/statements"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST"));
        assertThat(allowed.status()).isEqualTo(200);
        assertThat(allowed.header("Access-Control-Allow-Origin")).isEqualTo("http://localhost:4200");

        var denied = send(HttpRequest.newBuilder(uri("/api/statements"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST"));
        assertThat(denied.status()).isEqualTo(403);
    }

    @Test
    void errorsAreProblemDetailJson() {
        var r = json("POST", "/api/auth/login", null, "{\"email\":\"nobody@example.com\",\"password\":\"x\"}");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.header("Content-Type")).startsWith("application/problem+json");
    }

    @Test
    void h2ConsoleIsReachableWithoutToken() {
        assertThat(get("/h2-console/", null).status()).isEqualTo(200);
    }
}
