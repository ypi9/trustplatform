# Week 2 Test Script

## Prerequisites
- PostgreSQL running with `authdb` database
- App running: `./mvnw spring-boot:run`

---

## Flow A: Approve Verification

### Step 1: Register
```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@test.com", "password": "password123"}'
```
Expected: `201` — "User registered successfully"

### Step 2: Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@test.com", "password": "password123"}'
```
Expected: `200` — `{ "accessToken": "..." }`

Save the token:
```bash
export ALICE_TOKEN=<paste accessToken here>
```

### Step 3: Check initial status
```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer $ALICE_TOKEN"
```
Expected:
```json
{
  "userId": "...",
  "email": "alice@test.com",
  "isVerified": false,
  "verificationLevel": "NONE"
}
```

### Step 4: Submit verification
```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"documentUrl": "s3://fake-bucket/alice-id-card.png"}'
```
Expected: `200`
```json
{
  "requestId": "...",
  "status": "PENDING"
}
```

Save the requestId:
```bash
export ALICE_REQUEST_ID=<paste requestId here>
```

### Step 5: Check status = PENDING
```bash
curl -X GET http://localhost:8080/verification/status \
  -H "Authorization: Bearer $ALICE_TOKEN"
```
Expected:
```json
{
  "verificationLevel": "PENDING",
  "latestRequest": {
    "requestId": "...",
    "status": "PENDING",
    "documentUrl": "s3://fake-bucket/alice-id-card.png",
    "createdAt": "..."
  }
}
```

### Step 6: Admin approves
```bash
curl -X POST http://localhost:8080/verification/review \
  -H "Content-Type: application/json" \
  -d '{"requestId": "'$ALICE_REQUEST_ID'", "decision": "APPROVED", "reviewNotes": "Document looks valid"}'
```
Expected: `200`
```json
{
  "requestId": "...",
  "status": "APPROVED",
  "userVerificationLevel": "VERIFIED"
}
```

### Step 7: Confirm VERIFIED
```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer $ALICE_TOKEN"
```
Expected:
```json
{
  "userId": "...",
  "email": "alice@test.com",
  "isVerified": true,
  "verificationLevel": "VERIFIED"
}
```

```bash
curl -X GET http://localhost:8080/verification/status \
  -H "Authorization: Bearer $ALICE_TOKEN"
```
Expected:
```json
{
  "verificationLevel": "VERIFIED",
  "latestRequest": {
    "requestId": "...",
    "status": "APPROVED",
    "documentUrl": "s3://fake-bucket/alice-id-card.png",
    "createdAt": "..."
  }
}
```

### Step 8: Verified user cannot submit again
```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"documentUrl": "s3://fake-bucket/alice-another-doc.png"}'
```
Expected: `409`
```json
{
  "error": "Verified users cannot submit a new verification request",
  "status": 409
}
```

---

## Flow B: Reject Verification

### Step 1: Register second user
```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "bob@test.com", "password": "password123"}'
```
Expected: `201`

### Step 2: Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "bob@test.com", "password": "password123"}'
```
Save token:
```bash
export BOB_TOKEN=<paste accessToken here>
```

### Step 3: Submit verification
```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"documentUrl": "s3://fake-bucket/bob-id-card.png"}'
```
Save requestId:
```bash
export BOB_REQUEST_ID=<paste requestId here>
```

### Step 4: Admin rejects
```bash
curl -X POST http://localhost:8080/verification/review \
  -H "Content-Type: application/json" \
  -d '{"requestId": "'$BOB_REQUEST_ID'", "decision": "REJECTED", "reviewNotes": "Document is blurry"}'
```
Expected: `200`
```json
{
  "requestId": "...",
  "status": "REJECTED",
  "userVerificationLevel": "REJECTED"
}
```

### Step 5: Confirm REJECTED
```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer $BOB_TOKEN"
```
Expected:
```json
{
  "userId": "...",
  "email": "bob@test.com",
  "isVerified": false,
  "verificationLevel": "REJECTED"
}
```

---

## Flow C: Resubmit After Rejection

### Step 1: Rejected user submits again
```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"documentUrl": "s3://fake-bucket/bob-id-card-v2.png"}'
```
Expected: `200`
```json
{
  "requestId": "...",
  "status": "PENDING"
}
```

Save new requestId:
```bash
export BOB_REQUEST_ID_2=<paste requestId here>
```

### Step 2: Confirm profile back to PENDING
```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer $BOB_TOKEN"
```
Expected:
```json
{
  "userId": "...",
  "email": "bob@test.com",
  "isVerified": false,
  "verificationLevel": "PENDING"
}
```

### Step 3: Admin can view all pending requests
```bash
curl -X GET http://localhost:8080/verification/requests?status=PENDING
```
Expected: List containing Bob's new request

---

## Edge Cases

### Duplicate pending submission blocked
```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"documentUrl": "s3://fake-bucket/bob-another-doc.png"}'
```
Expected: `409`
```json
{
  "error": "User already has a pending verification request",
  "status": 409
}
```

### Review already-reviewed request blocked
```bash
curl -X POST http://localhost:8080/verification/review \
  -H "Content-Type: application/json" \
  -d '{"requestId": "'$ALICE_REQUEST_ID'", "decision": "REJECTED", "reviewNotes": "Changed my mind"}'
```
Expected: `409`
```json
{
  "error": "Only pending verification requests can be reviewed. Current status: APPROVED",
  "status": 409
}
```

### Missing documentUrl
```bash
curl -X POST http://localhost:8080/verification/submit \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
```
Expected: `400`

### Invalid token
```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer invalidtoken"
```
Expected: `401` or `403`