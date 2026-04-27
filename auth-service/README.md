# TrustPlatform Auth Service

TrustPlatform Auth Service is a Spring Boot backend for authentication and identity verification. It supports user signup and login, private verification-document uploads to S3, verification request submission, and admin review through short-lived presigned links.

## Architecture

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

### Verification flow

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

## Architecture summary

- `auth`:
  JWT auth, login, signup, security config, authenticated user handling
- `user`:
  user entities, repositories, and profile response DTOs
- `verification`:
  verification workflow, request/review DTOs, admin review flow
- `storage`:
  document upload handling and private S3 integration
- `audit`:
  audit log persistence for business events
- `common`:
  shared exception handling and structured request/event logging

The service follows a clean controller -> service -> repository layering. Controllers remain thin; business rules live in services.

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
