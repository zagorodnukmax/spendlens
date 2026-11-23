package com.spendlens.it;

import com.spendlens.support.RealServerSupport;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Multipart upload and status polling over real HTTP, the same way the browser does it. */
abstract class StatementItSupport extends RealServerSupport {

    protected Response upload(String token, String fileName, byte[] content) {
        String boundary = "----spendlens" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);

        return send(request("/api/statements", token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    protected Response upload(String token, String fileName, String csv) {
        return upload(token, fileName, csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Polls GET /api/statements/{id} like the UI does, until processing has finished. */
    protected Response waitUntilProcessed(String token, int id) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            var r = get("/api/statements/" + id, token);
            if (!"PROCESSING".equals(r.json("$.status"))) {
                return r;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Statement " + id + " was not processed in time");
    }

    protected int uploadAndWait(String token, String csv) {
        var created = upload(token, "it.csv", csv);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        int id = created.json("$.id");
        assertThat((String) waitUntilProcessed(token, id).json("$.status")).isEqualTo("DONE");
        return id;
    }
}
