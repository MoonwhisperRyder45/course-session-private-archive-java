package edu.example.archive;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenants/{tenant}")
public class LessonController {
    private final CourseSessions sessions;
    public LessonController(CourseSessions sessions) { this.sessions = sessions; }

    @PostMapping("/onboard")
    public Object onboard(@PathVariable String tenant) { return sessions.onboard(tenant); }

    @PostMapping("/lessons/{lesson}/room")
    public Object open(@PathVariable String tenant, @PathVariable String lesson, @RequestParam String student) {
        return sessions.open(tenant, lesson, student);
    }

    @PostMapping("/lessons/{lesson}/recordings/{session}")
    public Object archive(@PathVariable String tenant, @PathVariable String lesson, @PathVariable String session) {
        return sessions.archive(tenant, lesson, session);
    }

    @DeleteMapping("/lessons/{lesson}/room")
    public Object close(@PathVariable String tenant, @PathVariable String lesson) { return sessions.close(tenant, lesson); }

    @ExceptionHandler(InfraiGateway.InfraiException.class)
    public ResponseEntity<?> rejected(InfraiGateway.InfraiException e) {
        int status = e.status >= 400 && e.status < 500 ? e.status : 502;
        return ResponseEntity.status(status).body(Map.of("code", e.code, "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }
}
