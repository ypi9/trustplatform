# TrustPlatform Auth Service

A JWT-based authentication and identity verification service built with Spring Boot. Features user registration, role-based access control (USER / ADMIN), file uploads, and a full document verification workflow with a state-machine engine.

---

## Table of Contents

- [Deployment Snapshot](#deployment-snapshot)
- [Prerequisites](#prerequisites)
- [Environment Variables](#environment-variables)
- [AWS Setup](#aws-setup)
- [Database Setup](#database-setup)
- [Run the App](#run-the-app)
- [Run Tests](#run-tests)
- [API Endpoints](#api-endpoints)
- [Request / Response Examples](#request--response-examples)
- [File Upload](#file-upload)
- [Verification Flow Diagram](#verification-flow-diagram)
- [Architecture Diagram](#architecture-diagram)
- [Architecture Summary](#architecture-summary)
- [Role-Based Access Control](#role-based-access-control)
- [Error Response Format](#error-response-format)
- [Audit Log](#audit-log)
- [Database Tables](#database-tables)
- [Demo Script (curl)](#demo-script-curl)
- [Postman Collection](#postman-collection)

---

## Deployment Snapshot

- Deployment target: **AWS Elastic Beanstalk (Corretto 21)**
- Deployed base URL: `http://trustplatform-dev.eba-ihrcejd2.us-east-2.elasticbeanstalk.com`
- Database: **Amazon RDS PostgreSQL** in `us-east-2`
- RDS engine: **PostgreSQL**
- File storage: **private Amazon S3** bucket in `us-east-2`
- Config management: **Elastic Beanstalk environment properties**
- Week 5 deployed subset confirmed working:
  - `/health`, `/live`, `/ready`
  - `/auth/signup`, `/auth/login`, `/auth/me`
  - `/files/upload`
  - `/verification/submit`
- Week 6 follow-up:
  - finish the full deployed admin review flow after redeploying the bootstrap-admin build and validating the promoted admin account end to end

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| PostgreSQL | 15+ |
| Maven | 3.9+ (or use the included `mvnw` wrapper) |
| AWS CLI | 2.x (for local S3 access) |

## Environment Variables

All secrets and environment-specific values are externalized. Create a local `.env` file or export the variables in your shell:

```bash
cd auth-service
touch .env
# Edit .env with your local values
```

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://...` | Full JDBC connection string (preferred in deployment) |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `authdb` | Database name |
| `DB_USERNAME` | `yixupi` | Database user |
| `DB_PASSWORD` | *(empty)* | Database password |
| `JWT_SECRET` | *(dev key)* | HMAC signing key (256-bit+) |
| `JWT_EXPIRATION` | `3600000` | Token TTL in ms (1 hour) |
| `AWS_REGION` | `us-east-2` | AWS region |
| `AWS_S3_BUCKET` | `your-private-s3-bucket` | S3 bucket name |
| `AWS_ACCESS_KEY_ID` | *(none)* | Optional local AWS access key |
| `AWS_SECRET_ACCESS_KEY` | *(none)* | Optional local AWS secret key |
| `AWS_PROFILE` | *(none)* | Optional named AWS profile for local development |
| `PORT` | `8080` locally / `5000` in Elastic Beanstalk | App listen port |
| `S3_BUCKET` | *(none)* | Preferred deployed bucket variable |
| `S3_VALIDATE_ON_STARTUP` | `false` | Optional startup S3 validation |
| `JPA_SHOW_SQL` | `true` locally / `false` in prod | SQL logging flag |
| `BOOTSTRAP_ADMIN_EMAIL` | *(empty)* | Optional startup email to promote to `ADMIN` |

Export them in your shell, or use a `.env` loader. The `.env` file is gitignored.

For the deployed environment, the core DB connection variables are:

```text
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/<database-name>
DB_USERNAME=<db-user>
DB_PASSWORD=<db-password>
```

## AWS Setup

The service stores verification documents in **private AWS S3**. The backend stores durable S3 object keys and generates short-lived presigned URLs only when an admin needs to review a document. Do not make uploaded objects public.

### Required S3 bucket settings

1. Create a bucket in the configured `AWS_REGION`.
2. Keep **Block Public Access** fully enabled:
   - Block public ACLs
   - Ignore public ACLs
   - Block public bucket policies
   - Restrict public buckets
3. Keep bucket/object ACLs private. The app does not set public-read ACLs.
4. Give the backend IAM principal least-privilege access to the bucket.

Example IAM actions for the app:

```json
[
  "s3:PutObject",
  "s3:GetObject",
  "s3:HeadObject",
  "s3:ListBucket",
  "s3:GetBucketLocation",
  "s3:GetBucketPublicAccessBlock"
]
```

### Local development — configure credentials

The app uses the [Default Credential Provider Chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html), so any of these work:

**Option A: AWS CLI profile (recommended)**
```bash
aws configure
# Enter your Access Key ID, Secret Access Key, region (us-west-1)
# This stores credentials in ~/.aws/credentials
```

**Option B: Environment variables**
```bash
export AWS_ACCESS_KEY_ID=AKIAxxxxxxxxxxxxxxxx
export AWS_SECRET_ACCESS_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export AWS_REGION=us-west-1
```

### Verify access
```bash
aws s3 ls s3://trustplatform-uploads-1/
aws s3api get-public-access-block --bucket trustplatform-uploads-1
```

## Database Setup

### Deployed database

- Engine: **Amazon RDS PostgreSQL**
- Region: `us-east-2`
- Typical deployed connection shape:

```text
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/<database-name>
DB_USERNAME=<db-user>
DB_PASSWORD=<db-password>
```

### Required security group rule

For a private RDS deployment, the RDS security group should allow:

```text
Type: PostgreSQL
Port: 5432
Source: Elastic Beanstalk EC2 security group
```

Do **not** open the database to `0.0.0.0/0`.

### Warning

Do **not** expose the RDS instance publicly for normal operation.  
Keep `Publicly accessible = No` and allow database access only from the Elastic Beanstalk application security group or another intentional private path.

### MVP note

The app currently uses:

```text
spring.jpa.hibernate.ddl-auto=update
```

This is acceptable for MVP/demo development, but it is **not** the long-term production migration strategy.  
For a production-grade setup, use explicit database migrations and controlled schema rollout.

```bash
# Start PostgreSQL (macOS Homebrew)
brew services start postgresql@17

# Create the database
createdb authdb

# (Optional) Create an admin user after first signup — see "RBAC" section below
```

## Run the App

```bash
cd auth-service
./mvnw spring-boot:run
```

The app starts at **http://localhost:8080**.

For the deployed environment, use:

```text
http://trustplatform-dev.eba-ihrcejd2.us-east-2.elasticbeanstalk.com
```

## Run Tests

```bash
cd auth-service
./mvnw test
```

The integration test (`VerificationFlowIntegrationTest`) runs full verification flows against the configured PostgreSQL database while using an in-memory fake S3 service for deterministic uploads and presigned links.

Covered flows:

- Flow A: register user, login, upload verification file, submit verification request, confirm `document_key` metadata in the database, login as admin, list requests, generate a presigned document link, approve the request.
- Flow B: upload an invalid file type and confirm the request is rejected.
- Flow C: upload an oversized file and confirm the request is rejected.

For a real demo, use an actual PNG, JPEG, or PDF file. Placeholder text with `image/png` will fail the file-signature check.

### Week 5 end-to-end testing status

- **Local/integration coverage:** full Flow A/B/C through `VerificationFlowIntegrationTest`
- **Deployed environment confirmed:** register user, login, upload verification doc, submit verification
- **Remaining deployed gap for Week 6:** complete the admin-side document-link generation and review/approve loop after redeploying the bootstrap-admin build and validating admin promotion in Elastic Beanstalk

### Suggested deployed test flow

1. Register user
2. Login
3. Upload verification document
4. Submit verification request
5. Login as admin
6. List verification requests
7. Generate a presigned document link
8. Approve or reject the request
9. Check the user verification status

---

## API Endpoints

### Auth

| Method | Endpoint | Auth | Status | Description |
|--------|----------|------|--------|-------------|
| POST | `/auth/signup` | — | 201 | Register a new user |
| POST | `/auth/login` | — | 200 | Login and receive a JWT |
| GET | `/auth/me` | Bearer | 200 | Get current user profile |

### File Upload

| Method | Endpoint | Auth | Status | Description |
|--------|----------|------|--------|-------------|
| POST | `/files/upload` | Bearer | 200 | Upload a document (multipart/form-data) |

### Verification

| Method | Endpoint | Auth | Status | Description |
|--------|----------|------|--------|-------------|
| POST | `/verification/submit` | Bearer | 200 | Submit a verification request |
| GET | `/verification/status` | Bearer | 200 | Check verification status |
| POST | `/verification/review` | Bearer (ADMIN) | 200 | Approve or reject a request |
| GET | `/verification/requests` | Bearer (ADMIN) | 200 | List all requests (`?status=PENDING`) |
| GET | `/verification/requests/{id}/document-link` | Bearer (ADMIN) | 200 | Generate a short-lived presigned document URL |

### Health

| Method | Endpoint | Auth | Status | Description |
|--------|----------|------|--------|-------------|
| GET | `/health` | — | 200 | Health check |
| GET | `/live` | — | 200 | Lightweight liveness probe |
| GET | `/ready` | — | 200 / 503 | Readiness check for DB + S3 |

---

## Request / Response Examples

### POST /auth/signup

```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@test.com", "password": "password123"}'
```

**201 Created**
```
User created
```

### POST /auth/login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@test.com", "password": "password123"}'
```

**200 OK**
```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9..."
}
```

### GET /auth/me

```bash
curl http://localhost:8080/auth/me \
  -H "Authorization: Bearer <token>"
```

**200 OK**
```json
{
  "userId": "550e8400-e29b-...",
  "email": "alice@test.com",
  "verified": false,
  "verificationLevel": "NONE"
}
```

### POST /files/upload

```bash
curl -X POST http://localhost:8080/files/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@/path/to/id-card.png"
```

**200 OK**
```json
{
  "fileUrl": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png",
  "bucket": "trustplatform-uploads-1",
  "objectKey": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png",
  "originalFilename": "id-card.png",
  "contentType": "image/png",
  "size": 184231
}
```

### POST /verification/submit

```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"documentKey": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png"}'
```

**200 OK**
```json
{
  "requestId": "uuid",
  "status": "PENDING",
  "documentKey": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png",
  "documentOriginalName": "id-card.png",
  "documentContentType": "image/png",
  "documentSize": 184231,
  "documentUrl": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png"
}
```

### GET /verification/status

```bash
curl http://localhost:8080/verification/status \
  -H "Authorization: Bearer <token>"
```

**200 OK**
```json
{
  "verificationLevel": "PENDING",
  "latestRequest": {
    "requestId": "uuid",
    "status": "PENDING",
    "documentKey": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png",
    "documentOriginalName": "id-card.png",
    "documentContentType": "image/png",
    "documentSize": 184231,
    "documentUrl": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png",
    "createdAt": "2026-04-14T10:30:00Z"
  }
}
```

### POST /verification/review  *(ADMIN only)*

```bash
curl -X POST http://localhost:8080/verification/review \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "requestId": "uuid",
    "decision": "APPROVED",
    "reviewNotes": "Document looks valid"
  }'
```

**200 OK**
```json
{
  "requestId": "uuid",
  "status": "APPROVED",
  "userVerificationLevel": "VERIFIED",
  "reviewNotes": "Document looks valid",
  "reviewedAt": "2026-04-14T10:35:00Z"
}
```

### GET /verification/requests  *(ADMIN only)*

```bash
curl "http://localhost:8080/verification/requests?status=PENDING" \
  -H "Authorization: Bearer <admin-token>"
```

**200 OK**
```json
[
  {
    "requestId": "uuid",
    "userId": "uuid",
    "status": "PENDING",
    "documentKey": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png",
    "documentOriginalName": "id-card.png",
    "documentContentType": "image/png",
    "documentSize": 184231,
    "documentUrl": "uploads/2026/04/22/8f3d9b2b-54f5-4e1e-b9b5-0df0d4b7a4b5.png",
    "createdAt": "2026-04-14T10:30:00Z",
    "reviewedAt": null
  }
]
```

### GET /verification/requests/{id}/document-link  *(ADMIN only)*

```bash
curl http://localhost:8080/verification/requests/<request-id>/document-link \
  -H "Authorization: Bearer <admin-token>"
```

**200 OK**
```json
{
  "requestId": "uuid",
  "downloadUrl": "https://..."
}
```

The `downloadUrl` is a short-lived presigned S3 GET URL, currently valid for 15 minutes. It is generated on demand and is never stored in the database.

---

## File Upload

| Rule | Value |
|------|-------|
| Accepted types | `image/png`, `image/jpeg`, `application/pdf` |
| Max size | 5 MB |
| Multipart config | `spring.servlet.multipart.max-file-size=5MB`, `spring.servlet.multipart.max-request-size=5MB` |
| Storage | Private S3 bucket configured by `AWS_S3_BUCKET` / `AWS_REGION` |
| Object key format | `uploads/<yyyy>/<MM>/<dd>/<uuid>.<ext>` |
| Review access | Admin-only presigned S3 URL, generated on demand |

Upload a file **first**, then pass the returned `objectKey` as `documentKey` to `/verification/submit`. The service validates the object exists in S3 and stores trusted metadata from S3:

- `document_key`
- `document_original_name`
- `document_content_type`
- `document_size`

The backend never returns a permanent public S3 URL as the durable document reference. `fileUrl` and `documentUrl` are legacy compatibility fields and currently contain the S3 object key, not a browser-openable public URL.

Validation happens in layers:

- Spring multipart limits reject oversized requests early.
- `S3StorageService` rejects empty files, disallowed content types, files over 5 MB, and mismatched PNG/JPEG/PDF signatures.
- Original filenames are sanitized before being stored as object metadata.

---

## Verification Flow Diagram

```
┌──────────┐
│   NONE   │  ← user just registered
└────┬─────┘
     │ upload file + submit verification
     ▼
┌──────────┐
│ PENDING  │  ← waiting for admin review
└────┬─────┘
     │
     ├── admin approves ──────► ┌──────────┐
     │                          │ VERIFIED  │  ← verified = true
     │                          └──────────┘
     │                              ✗ cannot submit again
     │
     └── admin rejects ───────► ┌──────────┐
                                │ REJECTED  │  ← verified = false
                                └────┬──────┘
                                     │ upload new file + resubmit
                                     ▼
                                ┌──────────┐
                                │ PENDING  │  ← new cycle
                                └──────────┘
```

### Transition Rules

| From | To | Allowed? |
|------|----|----------|
| NONE | PENDING | ✅ user submits |
| PENDING | VERIFIED | ✅ admin approves |
| PENDING | REJECTED | ✅ admin rejects |
| REJECTED | PENDING | ✅ user resubmits |
| VERIFIED | submit | ❌ blocked |
| PENDING | submit | ❌ blocked (wait for review) |

---

## Architecture Diagram

```text
Browser / Postman / curl
          |
          v
 AWS Elastic Beanstalk (Spring Boot auth-service)
          |
          +--> Amazon RDS PostgreSQL
          |
          +--> Amazon S3 (private verification documents)
                     |
                     +--> Presigned GET URLs for admin review
```

## Architecture Summary

The backend runs on AWS Elastic Beanstalk in `us-east-2` as a Spring Boot REST service.  
User accounts, profiles, verification requests, and audit logs are stored in Amazon RDS PostgreSQL.  
Verification documents are stored privately in Amazon S3, and the database keeps durable object keys plus metadata instead of public file URLs.  
Configuration is managed through environment variables in Elastic Beanstalk, with secrets such as DB credentials and JWT keys kept out of git.  
Health is split into `/live` for liveness, `/health` for lightweight service status, and `/ready` for DB + S3 readiness.  
Week 5 delivered a working deployed backend for auth, upload, and verification submission in AWS.  
Week 6 should close the loop on deployed admin review and any final IAM and observability polish.

---

## Role-Based Access Control

| Role | Capabilities |
|------|-------------|
| `USER` (default) | signup, login, /me, upload file, submit/status verification |
| `ADMIN` | All USER capabilities + review requests, list all requests, generate presigned document links |

### How to create an admin

There is no admin-signup endpoint. For local/manual setups, promote a user via SQL after they register:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

For deployed environments without direct DB access, you can also set:

```text
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
```

and redeploy or restart the app. On startup, the configured existing user will be promoted to `ADMIN`.

**Important:** The user must log in **after** the role change to receive a JWT that includes the `ADMIN` role claim.

---

## Error Response Format

All errors return a consistent JSON structure:

```json
{
  "timestamp": "2026-04-14T10:30:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "documentKey is required"
}
```

Validation errors include an additional `fields` map:

```json
{
  "timestamp": "2026-04-14T10:30:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fields": {
    "email": "must not be blank",
    "password": "must not be blank"
  }
}
```

| Status | Meaning | Example |
|--------|---------|---------|
| 400 | Bad Request | Missing/invalid input, file type not allowed |
| 401 | Unauthorized | Invalid email or password |
| 403 | Forbidden | Missing JWT, or USER accessing ADMIN endpoint |
| 404 | Not Found | User, profile, or verification request not found |
| 409 | Conflict | Duplicate email, already pending/verified |
| 500 | Internal Server Error | Unexpected server error |

---

## Audit Log

All significant actions are recorded in the `audit_log` table:

| Action | Trigger |
|--------|---------|
| `user_registered` | Successful signup |
| `login_success` | Successful login |
| `login_failed` | Wrong email or password |
| `file_uploaded_to_s3` | File uploaded via `/files/upload` |
| `verification_submitted` | Verification request created |
| `document_link_generated` | Admin generated a presigned document link |
| `verification_approved` | Admin approves request |
| `verification_rejected` | Admin rejects request |

---

## Database Tables

| Table | Description |
|-------|-------------|
| `users` | User accounts (email, password hash, role) |
| `user_profile` | Verification level, verified flag |
| `verification_requests` | Submission history, S3 document key/metadata, status, review notes |
| `audit_log` | Timestamped action trail |

---

## Demo Script (curl)

A complete happy-path walkthrough — copy-paste into your terminal:

### Deployed environment quick test

```bash
export BASE_URL=http://trustplatform-dev.eba-ihrcejd2.us-east-2.elasticbeanstalk.com

curl "$BASE_URL/health"
curl "$BASE_URL/live"
curl "$BASE_URL/ready"

curl -X POST "$BASE_URL/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{"email":"demo-user@example.com","password":"password123"}'

export TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"demo-user@example.com","password":"password123"}' | jq -r '.accessToken')

curl -X GET "$BASE_URL/auth/me" \
  -H "Authorization: Bearer $TOKEN"

UPLOAD_RESPONSE=$(curl -s -X POST "$BASE_URL/files/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/absolute/path/to/document.pdf")

echo "$UPLOAD_RESPONSE"

export OBJECT_KEY=$(echo "$UPLOAD_RESPONSE" | jq -r '.objectKey')

curl -X POST "$BASE_URL/verification/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"documentKey\":\"$OBJECT_KEY\"}"
```

```bash
BASE=http://localhost:8080

# ── 1. Register ──
curl -s -X POST "$BASE/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{"email": "demo@test.com", "password": "password123"}' && echo

# ── 2. Login ──
TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email": "demo@test.com", "password": "password123"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
echo "Token: ${TOKEN:0:20}..."

# ── 3. Check initial status ──
curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# ── 4. Upload a real PNG, JPG, or PDF file to private S3 ──
DOCUMENT_KEY=$(curl -s -X POST "$BASE/files/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/id-card.png" | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['objectKey'])")
echo "Document key: $DOCUMENT_KEY"

# ── 5. Submit verification ──
REQUEST_ID=$(curl -s -X POST "$BASE/verification/submit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"documentKey\": \"$DOCUMENT_KEY\"}" | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['requestId'])")
echo "Request ID: $REQUEST_ID"

# ── 6. Admin lists requests and generates a document review link ──
# First: psql -d authdb -c "UPDATE users SET role='ADMIN' WHERE email='demo@test.com';"
# Then re-login to get admin token:
# ADMIN_TOKEN=$(curl -s -X POST "$BASE/auth/login" ...)
curl -s "$BASE/verification/requests?status=PENDING" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool

DOWNLOAD_URL=$(curl -s "$BASE/verification/requests/$REQUEST_ID/document-link" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['downloadUrl'])")
echo "Open this short-lived URL in a browser:"
echo "$DOWNLOAD_URL"

# ── 7. Admin approves or rejects ──
curl -s -X POST "$BASE/verification/review" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"requestId\": \"$REQUEST_ID\", \"decision\": \"APPROVED\", \"reviewNotes\": \"Valid\"}" | \
  python3 -m json.tool

# ── 8. Confirm verified ──
curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

---

## Postman Collection

A ready-to-use Postman collection is included at:

```
postman/TrustPlatform-Auth-Service.postman_collection.json
```

### Variables

| Variable | Description |
|----------|-------------|
| `baseUrl` | `http://localhost:8080` |
| `accessToken` | JWT from login (regular user) |
| `adminToken` | JWT from login (admin user) |
| `documentKey` | S3 object key returned by upload |
| `verificationRequestId` | Request ID returned by verification submit |
| `fileUrl` | Legacy compatibility alias for the S3 object key |

### Folders

| Folder | Requests |
|--------|----------|
| **Auth** | Signup, Login, Me (no token / invalid / valid) |
| **Files** | Upload success, Upload no token, Upload invalid type |
| **Verification** | Submit, Submit invalid, Status, Get document link, Review (as user 403), Review approve, Review reject, Review already-reviewed 409, List all, List by PENDING |

Import the JSON file into Postman → set variables → run requests in order.

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.5 |
| Language | Java 21 |
| Database | PostgreSQL |
| Auth | JWT (jjwt 0.12.6) |
| Security | Spring Security (stateless, role-based) |
| Validation | Jakarta Bean Validation |
| ORM | Spring Data JPA / Hibernate |
| Build | Maven |
