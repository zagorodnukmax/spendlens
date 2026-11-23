package com.spendlens.auth;

import com.spendlens.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerLoginAndUseToken() {
        mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"maks@example.com\",\"password\":\"secret123\",\"displayName\":\"Maks\"}")
                .exchange();
        var login = mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"MAKS@example.com\",\"password\":\"secret123\"}").exchange();
        assertThat(login).hasStatusOk();
        assertThat(login).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");

        String token = read(login, "$.accessToken");
        assertThat(get("/api/me", token)).bodyJson().extractingPath("$.displayName").isEqualTo("Maks");
    }

    @Test
    void wrongPasswordIs401AndDuplicateEmailIs409() {
        String body = "{\"email\":\"dup@example.com\",\"password\":\"secret123\",\"displayName\":\"D\"}";
        mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(409);
        assertThat(mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dup@example.com\",\"password\":\"wrong-pass\"}")).hasStatus(401);
    }
}
