package edu.example.archive;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CourseSessionsTest {
    @Test
    void recordingBelongsToItsTenantAndLessonAndGetsAnIdempotentPutSignature() throws Exception {
        ObjectMapper json = new ObjectMapper();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<JsonNode> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            payload.set(json.readTree(exchange.getRequestBody()));
            byte[] response = "{\"ok\":true,\"data\":{\"url\":\"https://upload.example/lesson\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            CourseSessions sessions = new CourseSessions(new InfraiGateway(json,
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-key"));
            Map<String, Object> result = sessions.archive("academy7", "algebra", "class9");
            assertEquals("courses-academy7", result.get("bucket"));
            assertEquals("algebra/class9.webm", result.get("key"));
            assertEquals("/v1/storage/object/presign/courses-academy7/algebra%2Fclass9.webm", path.get());
            assertEquals("put", payload.get().path("op").asText());
            assertEquals("academy7-algebra-class9", payload.get().path("idempotency_key").asText());
            assertThrows(IllegalArgumentException.class, () -> sessions.archive("../other", "algebra", "class9"));
        } finally {
            server.stop(0);
        }
    }
}
