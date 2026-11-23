package com.spendlens.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Full Spring context on in-memory H2, no demo data, AI disabled (rules only). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false",
        "app.ai.api-key="
})
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    @Autowired
    protected MockMvcTester mvc;

    protected String registerUser() {
        var result = mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"u-%s@example.com","password":"secret123","displayName":"Test"}
                        """.formatted(UUID.randomUUID()))
                .exchange();
        assertThat(result).hasStatus(201);
        return read(result, "$.accessToken");
    }

    protected MvcTestResult upload(String token, String fileName, String csv) {
        var file = new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mvc.post().uri("/api/statements").multipart().file(file)
                .header("Authorization", "Bearer " + token).exchange();
    }

    protected MvcTestResult get(String uri, String token) {
        var req = mvc.get().uri(uri);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return req.exchange();
    }

    protected MvcTestResult postJson(String uri, String token, String json) {
        return mvc.post().uri(uri).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    /** Uploads and waits until background processing has finished. */
    protected long uploadAndWait(String token, String csv) {
        var created = upload(token, "test.csv", csv);
        assertThat(created).hasStatus(201);
        long id = ((Number) read(created, "$.id")).longValue();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            String status = read(get("/api/statements/" + id, token), "$.status");
            if (!status.equals("PROCESSING")) {
                assertThat(status).isEqualTo("DONE");
                return id;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("Statement was not processed in time");
    }

    protected static <T> T read(MvcTestResult result, String jsonPath) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
