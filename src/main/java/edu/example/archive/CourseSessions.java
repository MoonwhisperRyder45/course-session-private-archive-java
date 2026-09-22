package edu.example.archive;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CourseSessions {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    private final InfraiGateway api;

    public CourseSessions(InfraiGateway api) { this.api = api; }

    static String checked(String id) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Use lowercase letters, digits and hyphens (1-40 characters)");
        return id;
    }

    static String bucket(String tenant) { return "courses-" + checked(tenant); }
    static String room(String tenant, String lesson) { return checked(tenant) + "-" + checked(lesson); }
    static String objectKey(String lesson, String session) {
        return checked(lesson) + "/" + checked(session) + ".webm";
    }

    public JsonNode onboard(String tenant) {
        return api.post("/v1/storage/bucket/create", Map.of("name", bucket(tenant)), false);
    }

    public Map<String, Object> open(String tenant, String lesson, String student) {
        String name = room(tenant, lesson);
        JsonNode created = api.post("/v1/rtc/room/create", Map.of("name", name, "max_participants", 30, "empty_timeout_s", 600), false);
        JsonNode issued = api.post("/v1/rtc/token/issue", Map.of("room", name, "identity", checked(student),
            "display_name", student, "ttl_s", 3600, "can_publish", true, "can_subscribe", true), false);
        return Map.of("room", name, "roomDetails", created, "participantToken", issued);
    }

    public Map<String, Object> archive(String tenant, String lesson, String session) {
        String key = objectKey(lesson, session);
        // The lesson client PUTs its recording to this signed URL, directly into the tenant bucket.
        JsonNode signed = api.post("/v1/storage/object/presign/" + InfraiGateway.segment(bucket(tenant))
            + "/" + InfraiGateway.segment(key), Map.of("op", "put", "expires_seconds", 900,
                "content_type", "video/webm", "idempotency_key", tenant + "-" + lesson + "-" + session), true);
        return Map.of("bucket", bucket(tenant), "key", key, "upload", signed);
    }

    public JsonNode close(String tenant, String lesson) {
        return api.delete("/v1/rtc/room/delete/" + InfraiGateway.segment(room(tenant, lesson)));
    }
}
