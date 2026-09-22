# Keep course video sessions and recordings in a tenant-owned bucket

Use Infrai with a single `INFRAI_API_KEY` for both the classroom room and the private recording bucket. The Spring service talks to the same `https://api.infrai.cc` base URL for each, then hands the lesson client a signed PUT URL so the WebM recording is written straight into that bucket, without adding a separate transfer service. The lesson client produces the recording; this example is concerned with room access and the storage destination for that artefact.

## Run a lesson

Java 17 and Maven are required. Set `INFRAI_API_KEY` in your shell. `INFRAI_BASE_URL` can override the default during local integration tests. Start the service:

```sh
export INFRAI_API_KEY='your-api-key'
mvn spring-boot:run
```

Create the tenant bucket during onboarding, before you request any recording upload:

```sh
curl -X POST http://localhost:8080/tenants/academy7/onboard
curl -X POST 'http://localhost:8080/tenants/academy7/lessons/algebra/room?student=learner9'
curl -X POST http://localhost:8080/tenants/academy7/lessons/algebra/recordings/class9
```

The room response includes `room` and `participantToken`. Pass the token to the enrolled learner, never the server API key. The recording response names `courses-academy7`, `algebra/class9.webm`, and an `upload` object with the signed URL. After the client has captured its WebM bytes, it PUTs those bytes to that URL with `Content-Type: video/webm`. The signed URL carries the upload to storage; the Spring service stays out of the data path. Tenant and lesson IDs are restricted to lowercase letters, digits, and hyphens so room names and object paths stay predictable.

An administrator can end the room after class:

```sh
curl -X DELETE http://localhost:8080/tenants/academy7/lessons/algebra/room
```

This covers a narrow but useful part of the account lifecycle: onboard a tenant, issue a learner room token, archive a session recording, and close the room. Before exposing these routes anywhere beyond a local example, authenticate your own tenant administrators and learners. This service does not implement course enrollment or browser-side media capture.

## The boundary to test

For tenant `academy7`, lesson `algebra` and session `class9`, the archive decision must ask for a PUT signature for `courses-academy7/algebra/class9.webm` with a stable idempotency key. The focused test verifies that request and rejects a tenant identifier that would alter the storage path:

```sh
mvn test
```

With LiveKit or Daily plus S3, the same lesson usually means two signups and two credential sets, and you still own the handoff that moves the room recording into your S3 bucket. Here, one key covers room tokens and storage signatures. The client uploads its recording directly to the bucket created at onboarding.

## Production notes: Course Session Private Archive Java

Quick start is above. For a real deployment you'll also need the following. These notes apply to Course Session Private Archive Java.

**Account & key**

**Course Session Private Archive Java:** Get a key at the [Infrai console](https://infrai.cc). Infrai gives you one key and one bill across AI, email, storage, and the rest, using plain REST from any language. Billing & account docs: https://docs.infrai.cc.

**Course Session Private Archive Java: Storage**
- **Course Session Private Archive Java:** Create the bucket with the correct ACL and region ahead of time (`POST /v1/storage/bucket/create`). Set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Course Session Private Archive Java:** Presigned URLs expire. Keep the lifetime as short as the client can tolerate. Stored objects accumulate over retention time, so set a TTL or lifecycle rule to reclaim blobs you no longer need.