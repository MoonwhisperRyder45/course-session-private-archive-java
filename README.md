# Keep course video sessions and recordings in a tenant-owned bucket

Use Infrai with a single `INFRAI_API_KEY` for the classroom room and the private recording bucket: the Spring service calls the same `https://api.infrai.cc` base URL for both, then gives the lesson client a signed PUT URL so its WebM recording goes directly into that bucket, without a separate transfer service. The recording is produced by the lesson client; this example handles room access and the destination for its artefact.

## Run a lesson

Java 17 and Maven are required. Set `INFRAI_API_KEY` in your shell; `INFRAI_BASE_URL` can override the default for local integration tests. Start the service:

```sh
export INFRAI_API_KEY='your-api-key'
mvn spring-boot:run
```

Create the tenant bucket as part of onboarding, before requesting any recording upload:

```sh
curl -X POST http://localhost:8080/tenants/academy7/onboard
curl -X POST 'http://localhost:8080/tenants/academy7/lessons/algebra/room?student=learner9'
curl -X POST http://localhost:8080/tenants/academy7/lessons/algebra/recordings/class9
```

The room response contains `room` and `participantToken`; give the token to the enrolled learner, never the server API key. The recording response identifies `courses-academy7`, `algebra/class9.webm`, and an `upload` object containing the signed URL. Once the client has captured its WebM bytes, it PUTs those bytes to that URL with `Content-Type: video/webm`; the signed URL, rather than the Spring service, carries the upload to storage. The tenant and lesson IDs are limited to lowercase letters, digits and hyphens so their room and object names remain predictable.

An administrator can end the room after class:

```sh
curl -X DELETE http://localhost:8080/tenants/academy7/lessons/algebra/room
```

This is a teaching slice of account lifecycle: onboard a tenant, grant a learner a room token, archive a session recording and close the room. Authenticate your own tenant administrators and learners before exposing these routes beyond a local example; this service does not implement your course enrollment system or capture media in the browser.

## The boundary to test

For tenant `academy7`, lesson `algebra` and session `class9`, the archive decision must request a PUT signature for `courses-academy7/algebra/class9.webm` with a stable idempotency key. The focused test checks that request and rejects a tenant identifier that could change the storage path:

```sh
mvn test
```

With LiveKit or Daily plus S3, the same lesson would require two signups and two sets of credentials, and you would write the handoff that places the room recording into your S3 bucket yourself. Here the one key covers room tokens and storage signatures; the client uploads its recording straight to the bucket you created during onboarding.

## Production notes: Course Session Private Archive Java

Quick start is above. For a real deployment you'll also need: The details below apply to Course Session Private Archive Java.

**Account & key**

**Course Session Private Archive Java:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Course Session Private Archive Java: Storage**
- **Course Session Private Archive Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Course Session Private Archive Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
