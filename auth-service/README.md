# TrustPlatform Auth Service

A JWT-based authentication and identity verification service built with Spring Boot.

## Prerequisites

- Java 17+
- PostgreSQL
- Maven

## Database Setup

```bash
# Start PostgreSQL (if using Homebrew)
brew services start postgresql

# Create database
createdb -U <your_username> authdb
```

## Run the App

```bash
cd auth-service
./mvnw spring-boot:run
```

App starts at `http://localhost:8080`

## Run Tests

```bash
cd auth-service
./mvnw test
```

---

## API Endpoints

### Auth

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/auth/signup` | No | Register a new user |
| POST | `/auth/login` | No | Login and get JWT token |
| GET | `/auth/me` | Bearer token | Get current user info |

### Verification

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/verification/submit` | Bearer token | Submit verification request |
| GET | `/verification/status` | Bearer token | Check verification status |
| POST | `/verification/review` | No (admin) | Approve or reject a request |
| GET | `/verification/requests` | No (admin) | List all requests (optional ?status= filter) |

---

## Request/Response Examples

### POST /auth/signup
```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@test.com", "password": "password123"}'
```
Response: `201`
```
User registered successfully
```

### POST /auth/login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@test.com", "password": "password123"}'
```
Response: `200`
```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9..."
}
```

### GET /auth/me
```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer <token>"
```
Response: `200`
```json
{
  "userId": "550e8400-e29b-...",
  "email": "alice@test.com",
  "isVerified": false,
  "verificationLevel": "NONE"
}
```

### POST /verification/submit
```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"documentUrl": "s3://fake-bucket/alice-id-card.png"}'
```
Response: `200`
```json
{
  "requestId": "uuid",
  "status": "PENDING"
}
```

### GET /verification/status
```bash
curl -X GET http://localhost:8080/verification/status \
  -H "Authorization: Bearer <token>"
```
Response: `200`
```json
{
  "verificationLevel": "PENDING",
  "latestRequest": {
    "requestId": "uuid",
    "status": "PENDING",
    "documentUrl": "s3://fake-bucket/alice-id-card.png",
    "createdAt": "2026-04-02T17:15:54Z"
  }
}
```

### POST /verification/review
```bash
curl -X POST http://localhost:8080/verification/review \
  -H "Content-Type: application/json" \
  -d '{"requestId": "uuid", "decision": "APPROVED", "reviewNotes": "Document looks valid"}'
```
Response: `200`
```json
{
  "requestId": "uuid",
  "status": "APPROVED",
  "userVerificationLevel": "VERIFIED"
}
```

### GET /verification/requests
```bash
curl -X GET http://localhost:8080/verification/requests
curl -X GET http://localhost:8080/verification/requests?status=PENDING
```
Response: `200`
```json
[
  {
    "requestId": "uuid",
    "userId": "uuid",
    "status": "PENDING",
    "documentUrl": "s3://fake-bucket/alice-id-card.png",
    "createdAt": "2026-04-02T17:15:54Z",
    "reviewedAt": null
  }
]
```

---

## Error Responses

| Status | Meaning | Example |
|--------|---------|---------|
| 400 | Bad Request | Missing or invalid input |
| 401 | Unauthorized | Invalid email or password |
| 403 | Forbidden | Missing or invalid JWT token |
| 404 | Not Found | User or request not found |
| 409 | Conflict | Duplicate email, already pending/verified |

---

## Verification State Machine

```
┌──────────┐
│   NONE   │ ← user just registered
└────┬─────┘
     │ submit verification
     ▼
┌──────────┐
│ PENDING  │ ← waiting for admin review
└────┬─────┘
     │
     ├── admin approves ──────► ┌──────────┐
     │                          │ VERIFIED  │ ← isVerified = true
     │                          └──────────┘
     │                              ✗ cannot submit again
     │
     └── admin rejects ───────► ┌──────────┐
                                │ REJECTED  │ ← isVerified = false
                                └────┬──────┘
                                     │ can resubmit
                                     ▼
                                ┌──────────┐
                                │ PENDING  │ ← new request created
                                └──────────┘
```

### Transition Rules
- `NONE → PENDING` ✅ user submits verification
- `PENDING → VERIFIED` ✅ admin approves
- `PENDING → REJECTED` ✅ admin rejects
- `REJECTED → PENDING` ✅ user resubmits
- `VERIFIED → submit` ❌ blocked
- `PENDING → submit` ❌ blocked (must wait for review)

---

## Audit Log

The following actions are recorded in the `audit_logs` table:

| Action | Trigger |
|--------|---------|
| `user_registered` | User signs up |
| `login_success` | Successful login |
| `login_failed` | Failed login (wrong email or password) |
| `verification_submitted` | User submits verification |
| `verification_approved` | Admin approves request |
| `verification_rejected` | Admin rejects request |

---

## Database Tables

| Table | Description |
|-------|-------------|
| `users` | User accounts (email, password hash) |
| `user_profile` | Profile info and verification level |
| `verification_requests` | Verification submission history |
| `audit_logs` | Action audit trail |

---

## Demo Test Flow

See [TEST_SCRIPT.md](TEST_SCRIPT.md) for the complete end-to-end test sequence.

### Quick test:
```bash
# 1. Register
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "demo@test.com", "password": "password123"}'

# 2. Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "demo@test.com", "password": "password123"}'

# 3. Copy accessToken, submit verification
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"documentUrl": "s3://fake-bucket/demo-id.png"}'

# 4. Admin approves
curl -X POST http://localhost:8080/verification/review \
  -H "Content-Type: application/json" \
  -d '{"requestId": "<requestId>", "decision": "APPROVED", "reviewNotes": "Valid"}'

# 5. Confirm verified
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer <token>"
```