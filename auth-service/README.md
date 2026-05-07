# TrustPlatform Auth Service

TrustPlatform Auth Service is a Spring Boot backend for authentication and identity verification. It supports user signup and login, private verification-document uploads to S3, verification request submission, and admin review through short-lived presigned links.

## Project description

TrustPlatform is a cloud-based identity verification platform built with Spring Boot, JWT authentication, Amazon RDS PostgreSQL, and private S3 document storage. It demonstrates a realistic admin-review workflow rather than a toy auth API: users register, upload identity documents, submit verification requests, and admins review those documents through short-lived presigned URLs.

## Architecture

The current system is a single Spring Boot service that owns authentication, verification workflow, document storage integration, and admin review APIs. It is deployed as one unit to AWS Elastic Beanstalk and integrates with RDS PostgreSQL for relational state and S3 for private document storage.

### System diagram

```text
Client / Browser / Postman / curl
                |
                v
      Spring Boot API (Elastic Beanstalk)
                |
        +-------+-------+
        |               |
        v               v
 RDS PostgreSQL       Amazon S3
 (users, requests,    (private verification
 audit logs)          documents)
```

### Components

```text
Client / Postman / curl
          |
          v
Spring Boot API
(AWS Elastic Beanstalk)
          |
          +--------------------> Amazon RDS PostgreSQL
          |                     - users
          |                     - user_profile
          |                     - verification_requests
          |                     - audit_log
          |
          +--------------------> Amazon S3
                                - private verification documents
                                - presigned admin review links
```

### Internal service structure

- `auth`:
  signup, login, JWT generation, security configuration, authenticated-user principal mapping
- `user`:
  user entity, profile entity, repositories, and profile-facing DTOs
- `verification`:
  verification state machine, submit/review/list flows, admin decision logic
- `storage`:
  file upload validation, S3 object-key handling, metadata lookup, presigned document-link generation
- `audit`:
  audit log persistence for important business events
- `common`:
  shared logging, error handling, request correlation, health checks, API envelope helpers

The service follows a controller -> service -> repository structure. Controllers stay thin and mostly translate HTTP to application calls. Business rules live in services, and persistence concerns stay in repositories.

### Data flow

```text
1. Client signs up or logs in through the API
2. API stores user/profile state in PostgreSQL
3. Authenticated user uploads a verification document through the API
4. API validates the file and writes the document to private S3
5. API stores only the S3 object key and metadata in PostgreSQL
6. User submits a verification request referencing that stored object key
7. Admin lists requests from PostgreSQL and asks for a presigned review link
8. API generates a short-lived S3 URL on demand for the admin
9. Admin approves or rejects; API updates verification state in PostgreSQL
```

### Authentication flow

```text
1. User calls POST /auth/login with email + password
2. API validates credentials against PostgreSQL
3. API issues a signed JWT containing user identity and role
4. Client sends Bearer token on later requests
5. JwtAuthenticationFilter validates the token on each protected request
6. Spring Security stores the authenticated principal in the SecurityContext
7. Controllers and method-level authorization use that principal and role
```

### JWT sharing model

For the MVP service split, `auth-service` is the single JWT issuer and downstream services validate the token locally.

- `auth-service` issues JWTs
- `user-service` verifies JWTs
- `verification-service` verifies JWTs

Expected token claims:

```json
{
  "userId": "...",
  "email": "...",
  "role": "USER",
  "exp": 1778176800
}
```

MVP choice:

- all services share the same `JWT_SECRET`
- each service validates signature, expiration, and role claims locally

Future upgrade:

- replace the shared symmetric secret with asymmetric signing
- publish validation keys through JWKS
- keep `auth-service` as the issuer and let downstream services verify via public keys

### Verification workflow

```text
User registers -> logs in -> uploads document -> submits verification
                                                |
                                                v
                                      verification_requests = PENDING
                                                |
                                      Admin reviews document
                                         /                \
                                        v                  v
                                  APPROVED            REJECTED
                                        |                  |
                                        v                  v
                             profile = VERIFIED    profile = REJECTED
```

## Key decisions

### Why JWT instead of server-side session auth

- This service is API-first and deployed behind a load-balanced cloud runtime, so stateless authentication keeps scaling and deployment simpler.
- JWT avoids server-side session storage and fits well with Postman, curl, browser clients, and future frontend/mobile consumers.
- Role information travels with the token, which keeps authorization checks straightforward for admin-only endpoints.

### Why S3 for verification documents

- Verification files are binary objects, not relational records, so object storage is a better fit than the database.
- S3 gives durable storage, predictable scaling, and a clean boundary between metadata in PostgreSQL and document bytes in object storage.
- Private buckets plus presigned URLs let admins review documents without making files publicly accessible.

### Why RDS PostgreSQL for the database

- The core domain is relational: users, profiles, verification requests, and audit logs have clear structured relationships.
- PostgreSQL is mature, easy to operate on AWS, and a natural fit for Spring Data JPA and transactional workflow updates.
- RDS removes a lot of operational overhead for backups, patching, and managed availability compared with self-hosting a database.

### Why start with a monolith

- The first problem to solve is workflow correctness, not service decomposition.
- One deployable service keeps local development, testing, deployment, and debugging much simpler while the domain is still evolving.
- Authentication, verification state changes, audit logging, and storage integration are tightly coupled enough at this stage that splitting early would mostly add coordination cost.

## Tradeoffs

- Chose a monolith first for speed and simplicity.
  This keeps the MVP easier to reason about, but it means scaling and team ownership are shared within one codebase for now.
- Chose JWT over sessions.
  This removes server-side session storage, but it also means token revocation and rotation need deliberate handling as the system matures.
- Chose S3 object storage for documents.
  This is the right fit for uploaded files, but it introduces cloud configuration, IAM concerns, and cross-service dependency on S3 availability.
- Chose presigned URLs instead of public document access.
  This greatly improves privacy and access control, but it adds a little extra complexity for admins because links are generated on demand and expire.
- Chose RDS PostgreSQL for transactional state.
  This gives strong consistency and familiar query capabilities, but it also means the service depends on a central relational database for core workflow steps.

## Future evolution

- Split document handling into a dedicated storage or verification service if the workflow grows significantly.
- Introduce asynchronous events or queues for audit fan-out, notification delivery, and heavier admin processing.
- Add stronger token lifecycle controls such as refresh tokens, revocation, or short-lived access tokens with rotation.
- Move from simple monolith pagination and listing toward richer admin search, filtering, and reporting APIs as operational load increases.

## Tech stack

- Java 21
- Spring Boot 3
- Spring Security
- JWT
- Spring Data JPA / Hibernate
- PostgreSQL
- Amazon S3
- AWS Elastic Beanstalk
- Amazon RDS PostgreSQL
- Maven
- JUnit + Spring Boot integration tests
- Postman

## API overview

Detailed future service-boundary contracts live in [docs/service-contracts.md](./docs/service-contracts.md).

### Public endpoints

- `POST /auth/signup`
- `POST /auth/login`
- `GET /health`
- `GET /live`
- `GET /ready`

### Authenticated user endpoints

- `GET /auth/me`
- `POST /files/upload`
- `POST /verification/submit`
- `GET /verification/status`

### Admin endpoints

- `POST /verification/review`
- `GET /verification/requests`
- `GET /verification/requests/{id}/document-link`

## Key API behaviors

- Authentication uses Bearer JWTs.
- Verification documents are stored in private S3 only.
- The API stores S3 object keys, not permanent public URLs.
- Admin document access is generated on demand with presigned GET URLs.
- Upload validation enforces allowed types and a 5 MB limit.
- Error responses use a standard JSON format:

```json
{
  "timestamp": "2026-04-27T21:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "documentKey is required",
  "path": "/verification/submit"
}
```

## Cloud setup

### Elastic Beanstalk

Deploy the Spring Boot jar to AWS Elastic Beanstalk using a Corretto 21 Java platform. Configure environment properties instead of hardcoding secrets.

Recommended environment properties:

```text
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/<database>
DB_USERNAME=<db-user>
DB_PASSWORD=<db-password>
JWT_SECRET=<32+ byte secret>
JWT_EXPIRATION=3600000
AWS_REGION=us-west-1
S3_BUCKET=<private-bucket-name>
S3_VALIDATE_ON_STARTUP=false
BOOTSTRAP_ADMIN_EMAIL=<optional-admin-email>
JPA_SHOW_SQL=false
```

### RDS PostgreSQL

Use Amazon RDS PostgreSQL for persistent app data.

Recommended setup:

- private RDS instance
- PostgreSQL port `5432`
- security group access only from the Elastic Beanstalk application security group
- `spring.jpa.hibernate.ddl-auto=update` for MVP/demo usage

### S3

Use a private S3 bucket for verification documents.

Required posture:

- Block Public Access fully enabled
- no public ACLs
- no public bucket policy for object reads
- app IAM role limited to the required bucket actions

Expected key format:

```text
verification/{userId}/{requestId}/{uuid}.png
```

Required app capabilities:

- `s3:PutObject`
- `s3:GetObject`
- `s3:HeadObject`
- `s3:ListBucket`
- `s3:GetBucketLocation`
- `s3:GetBucketPublicAccessBlock`

## Local development

### Prerequisites

- Java 21+
- PostgreSQL
- Maven or the included `mvnw`
- AWS credentials for S3 access

### Run locally

```bash
cd auth-service
./mvnw spring-boot:run
```

Default local URL:

```text
http://localhost:8080
```

### Configure environment

Important properties come from environment variables:

- `DB_URL` or `DB_HOST` / `DB_PORT` / `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION`
- `AWS_REGION`
- `S3_BUCKET` or `AWS_S3_BUCKET`
- `BOOTSTRAP_ADMIN_EMAIL`

The current defaults live in [application.yaml](/Users/yixupi/VSCode/trustplatform/auth-service/src/main/resources/application.yaml).

## Folder overview

- `src/main/java`:
  application code
- `src/main/resources`:
  Spring configuration
- `src/test/java`:
  integration and application tests
- `postman`:
  demo-ready Postman collection and environment
- `demo`:
  curl-based demo scripts and sample upload document

## How to test the system

### Automated tests

Run the full auth-service test suite:

```bash
cd auth-service
./mvnw test
```

The integration suite covers:

- signup and login
- upload validation
- verification submission
- admin request listing
- presigned document-link generation
- approval and rejection flows
- standardized API error handling

### Manual demo flow

1. Register a user with `POST /auth/signup`
2. Login with `POST /auth/login`
3. Upload a verification document with `POST /files/upload`
4. Submit the verification request with `POST /verification/submit`
5. Login as admin
6. List pending requests with `GET /verification/requests?status=PENDING`
7. Generate a presigned review link with `GET /verification/requests/{id}/document-link`
8. Approve the request with `POST /verification/review`
9. Check the user status with `GET /verification/status`

## Demo instructions

### 1. Register

```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### 2. Login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

Save the returned JWT as `USER_TOKEN`.

### 3. Upload document

Use a real PNG, JPEG, or PDF file.

```bash
curl -X POST http://localhost:8080/files/upload \
  -H "Authorization: Bearer USER_TOKEN" \
  -F "file=@/absolute/path/to/id-card.png"
```

Save the returned `objectKey` or `fileUrl` value as `DOCUMENT_KEY`.

### 4. Submit verification

```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"documentKey\": \"DOCUMENT_KEY\"
  }"
```

Save the returned `requestId` as `VERIFICATION_REQUEST_ID`.

### 5. Admin approve

Login as an admin account and save the JWT as `ADMIN_TOKEN`.

List requests:

```bash
curl -X GET "http://localhost:8080/verification/requests?status=PENDING" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

Get document link:

```bash
curl -X GET http://localhost:8080/verification/requests/VERIFICATION_REQUEST_ID/document-link \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

Approve:

```bash
curl -X POST http://localhost:8080/verification/review \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"requestId\": \"VERIFICATION_REQUEST_ID\",
    \"decision\": \"APPROVED\",
    \"reviewNotes\": \"Document looks good\"
  }"
```

### 6. Check status

```bash
curl -X GET http://localhost:8080/verification/status \
  -H "Authorization: Bearer USER_TOKEN"
```

Expected result after approval:

```json
{
  "verificationLevel": "VERIFIED"
}
```

## Postman collection

A Postman collection is included here:

- [TrustPlatform-Auth-Service.postman_collection.json](/Users/yixupi/VSCode/trustplatform/auth-service/postman/TrustPlatform-Auth-Service.postman_collection.json)

Import it into Postman and set:

- `baseUrl`
- `accessToken`
- `adminToken`
- `documentKey`
- `verificationRequestId`

## Presentability notes

- structured request and business-event logging are enabled
- API errors are standardized
- controllers are thin and layered cleanly
- DTOs are separated from entities
- private S3 usage is enforced for verification documents
