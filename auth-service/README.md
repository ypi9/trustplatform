# TrustPlatform Auth Service

A JWT-based authentication and identity verification service built with Spring Boot. Features user registration, role-based access control (USER / ADMIN), file uploads, and a full document verification workflow with a state-machine engine.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Database Setup](#database-setup)
- [Run the App](#run-the-app)
- [Run Tests](#run-tests)
- [API Endpoints](#api-endpoints)
- [Request / Response Examples](#request--response-examples)
- [File Upload](#file-upload)
- [Verification Flow Diagram](#verification-flow-diagram)
- [Role-Based Access Control](#role-based-access-control)
- [Error Response Format](#error-response-format)
- [Audit Log](#audit-log)
- [Database Tables](#database-tables)
- [Demo Script (curl)](#demo-script-curl)
- [Postman Collection](#postman-collection)

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| PostgreSQL | 15+ |
| Maven | 3.9+ (or use the included `mvnw` wrapper) |

## Database Setup

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

## Run Tests

```bash
cd auth-service
./mvnw test
```

The integration test (`VerificationFlowIntegrationTest`) runs two full end-to-end flows (approve + reject) against the real database including file uploads.

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

### Health

| Method | Endpoint | Auth | Status | Description |
|--------|----------|------|--------|-------------|
| GET | `/health` | — | 200 | Health check |

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
  "fileUrl": "/uploads/a1b2c3d4-id-card.png"
}
```

### POST /verification/submit

```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"fileUrl": "/uploads/a1b2c3d4-id-card.png"}'
```

**200 OK**
```json
{
  "requestId": "uuid",
  "status": "PENDING",
  "documentUrl": "/uploads/a1b2c3d4-id-card.png"
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
    "documentUrl": "/uploads/a1b2c3d4-id-card.png",
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
    "documentUrl": "/uploads/a1b2c3d4-id-card.png",
    "createdAt": "2026-04-14T10:30:00Z",
    "reviewedAt": null
  }
]
```

---

## File Upload

| Rule | Value |
|------|-------|
| Accepted types | `image/png`, `image/jpeg`, `application/pdf` |
| Max size | 5 MB |
| Storage | `uploads/` directory (auto-created) |
| URL format | `/uploads/<uuid>-<original-name>.<ext>` |

Upload a file **first**, then pass the returned `fileUrl` to `/verification/submit`. The service validates the file exists on disk before creating a verification request.

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

## Role-Based Access Control

| Role | Capabilities |
|------|-------------|
| `USER` (default) | signup, login, /me, upload file, submit/status verification |
| `ADMIN` | All USER capabilities + review requests, list all requests |

### How to create an admin

There is no admin-signup endpoint. Promote a user via SQL after they register:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

**Important:** The user must log in **after** the role change to receive a JWT that includes the `ADMIN` role claim.

---

## Error Response Format

All errors return a consistent JSON structure:

```json
{
  "timestamp": "2026-04-14T10:30:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "fileUrl is required"
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
| `file_uploaded` | File uploaded via `/files/upload` |
| `verification_submitted` | Verification request created |
| `verification_approved` | Admin approves request |
| `verification_rejected` | Admin rejects request |

---

## Database Tables

| Table | Description |
|-------|-------------|
| `users` | User accounts (email, password hash, role) |
| `user_profile` | Verification level, verified flag |
| `verification_requests` | Submission history (document URL, status, review notes) |
| `audit_log` | Timestamped action trail |

---

## Demo Script (curl)

A complete happy-path walkthrough — copy-paste into your terminal:

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

# ── 4. Upload a file ──
FILE_URL=$(curl -s -X POST "$BASE/files/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/id-card.png" | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['fileUrl'])")
echo "File URL: $FILE_URL"

# ── 5. Submit verification ──
REQUEST_ID=$(curl -s -X POST "$BASE/verification/submit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"fileUrl\": \"$FILE_URL\"}" | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['requestId'])")
echo "Request ID: $REQUEST_ID"

# ── 6. Admin approves (use admin token) ──
# First: psql -d authdb -c "UPDATE users SET role='ADMIN' WHERE email='demo@test.com';"
# Then re-login to get admin token:
# ADMIN_TOKEN=$(curl -s -X POST "$BASE/auth/login" ...)
curl -s -X POST "$BASE/verification/review" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"requestId\": \"$REQUEST_ID\", \"decision\": \"APPROVED\", \"reviewNotes\": \"Valid\"}" | \
  python3 -m json.tool

# ── 7. Confirm verified ──
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
| `fileUrl` | File URL returned by upload |

### Folders

| Folder | Requests |
|--------|----------|
| **Auth** | Signup, Login, Me (no token / invalid / valid) |
| **Files** | Upload success, Upload no token, Upload invalid type |
| **Verification** | Submit, Submit invalid, Status, Review (as user 403), Review approve, Review reject, Review already-reviewed 409, List all, List by PENDING |

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
