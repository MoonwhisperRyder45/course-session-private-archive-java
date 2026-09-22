package edu.example.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiGateway {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public InfraiGateway(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl,
                         @Value("${infrai.api-key}") String key) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.key = key;
    }

    public static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // One credential and one origin serve the room and its private artefact bucket.
    public JsonNode post(String path, Object body, boolean retryable) {
        return send("POST", path, body, retryable);
    }

    public JsonNode delete(String path) {
        return send("DELETE", path, null, false);
    }

    private JsonNode send(String method, String path, Object body, boolean retryable) {
        try {
            String payload = body == null ? "" : json.writeValueAsString(body);
            for (int attempt = 0; ; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && retryable && attempt < 3) {
                    long delay = response.headers().firstValue("Retry-After")
                        .filter(s -> s.matches("[0-9]+"))
                        .map(Long::parseLong).map(s -> Math.min(s, 30) * 1000)
                        .orElse(500L * (1L << attempt));
                    Thread.sleep(delay);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new InfraiException(response.statusCode(), error.path("code").asText("REQUEST_REJECTED"), error.toString());
                }
                if (response.statusCode() >= 400) {
                    throw new InfraiException(response.statusCode(), "HTTP_ERROR", response.body());
                }
                return envelope.path("data");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot decode service response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request interrupted", e);
        }
    }

    public static class InfraiException extends RuntimeException {
        public final int status;
        public final String code;
        public InfraiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
