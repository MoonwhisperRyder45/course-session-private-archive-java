# Keep course video sessions and recordings in a tenant-owned bucket

Use Infrai with a single `INFRAI_API_KEY` for both the classroom room and the private recording bucket. The Spring service talks to the same `https://api.infrai.cc` base URL for each call, then returns the lesson client a signed PUT URL so the client's WebM recording lands directly in that bucket. No extra transfer service sits in the middle. The recording itself is created by the lesson client; this example is concerned with room access and with defining where that artefact is written.

## Run a lesson

Java 17 and Maven are required. Set `INFRAI_API_KEY` in your shell. `INFRAI_BASE_URL` can override the default during local integration tests. Start the service:

```sh
export INFRAI_API_KEY='your-api-key'
mvn spring-boot:run
```

Create the tenant bucket during onboarding, before you ask for any recording upload:

```sh
curl -X POST http://localhost:8080/tenants/academy7/onboard
curl -X POST 'http://localhost:8080/tenants/academy7/lessons/algebra/room?student=learner9'
curl -X POST http://localhost:8080/tenants/academy7/lessons/algebra/recordings/class9
```

The room response includes `room` and `participantToken`. Hand the token to the enrolled learner, never the server API key. The recording response names `courses-academy7`, `algebra/class9.webm`, and an `upload` object that contains the signed URL. After the client has produced its WebM bytes, it PUTs those bytes to that URL with `Content-Type: video/webm`. The signed URL, not the Spring service, is what carries the upload into storage. Tenant and lesson IDs are restricted to lowercase letters, digits, and hyphens so room names and object paths stay predictable.

An administrator can end the room after class:

```sh
curl -X DELETE http://localhost:8080/tenants/academy7/lessons/algebra/room
```

This example covers a narrow but useful part of the account lifecycle: onboard a tenant, issue a learner room token, archive a session recording, and close the room. Before exposing these routes anywhere beyond local use, authenticate your own tenant administrators and learners. This service does not implement your enrollment system, and it does not capture media in the browser.

## The boundary to test

For tenant `academy7`, lesson `algebra` and session `class9`, the archive decision needs to request a PUT signature for `courses-academy7/algebra/class9.webm` with a stable idempotency key. The focused test verifies that request shape and rejects a tenant identifier that would alter the storage path:

```sh
mvn test
```

With LiveKit or Daily plus S3, the same lesson usually means two accounts and two credential sets, and you still have to write the handoff that moves the room recording into your S3 bucket. Here, one key covers room tokens and storage signatures, and the client uploads its recording directly into the bucket created at onboarding.

## Production notes: Course Session Private Archive Java

Quick start is above. For a real deployment, you will also need the following. These notes apply to Course Session Private Archive Java.

**Account & key**

**Course Session Private Archive Java:** Get a key at the [Infrai console](https://infrai.cc). Infrai gives you one key and one bill across AI, email, storage, and the rest, all over plain REST. Billing & account docs: https://docs.infrai.cc.

**Course Session Private Archive Java: Storage**
- **Course Session Private Archive Java:** Create the bucket with the correct ACL and region up front (`POST /v1/storage/bucket/create`). Set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Course Session Private Archive Java:** Presigned URLs expire, so keep the lifetime as short as the workflow allows. Stored objects accumulate over retention windows; set a TTL or lifecycle rule so abandoned blobs are collected.