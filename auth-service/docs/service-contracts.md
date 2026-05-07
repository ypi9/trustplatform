# Service Contracts

This document defines the future API contracts for the planned `user-service` and `verification-service` split. Today these calls are still handled inside the monolith through local interfaces and services. Later, the same contracts can be implemented as HTTP calls between services.

## Goals

- make service ownership explicit
- define stable request and response shapes early
- reduce refactor risk when internal interfaces become network calls

## JWT Validation Model

For the MVP split, `auth-service` remains the token issuer and downstream services validate the same JWT locally.

### Token issuer

- `auth-service` issues JWTs after successful login

### Token consumers

- `user-service` verifies incoming JWTs on protected endpoints
- `verification-service` verifies incoming JWTs on protected endpoints

### Shared JWT claims

All services should expect the token payload to include:

```json
{
  "userId": "7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf",
  "email": "user@example.com",
  "role": "USER",
  "exp": 1778176800
}
```

Required claims:

- `userId`: canonical user identifier used across services
- `email`: current authenticated email
- `role`: authorization role such as `USER` or `ADMIN`
- `exp`: token expiration timestamp

### MVP validation approach

For the MVP, all services share the same JWT signing secret.

Validation steps in each service:

1. read the Bearer token from the `Authorization` header
2. verify the token signature using the shared `JWT_SECRET`
3. verify expiration via `exp`
4. extract `userId`, `email`, and `role`
5. build the local authenticated principal or security context
6. enforce endpoint and role-based authorization locally

Operational note:

- this is simple and fast for an MVP
- it also means secret distribution must be handled carefully because every validating service needs the same signing secret

### Future upgrade path

Once the system splits into independently deployed services, move away from a shared symmetric secret and adopt asymmetric signing.

Preferred future model:

- `auth-service` signs tokens with a private key
- `user-service` and `verification-service` validate tokens with a public key
- public keys are distributed through JWKS

Benefits of the future model:

- no need to copy the signing secret into every service
- easier key rotation
- cleaner separation between issuer and verifiers
- safer multi-service security posture

## User Service Internal API

The future `user-service` owns profile state and verification summary data. Other services should not update user-profile storage directly.

### Update verification status

`PATCH /internal/users/{userId}/verification-status`

Updates the verification summary stored for a user profile after the verification workflow changes state.

Path params:

- `userId`: UUID of the subject user

Request body:

```json
{
  "verificationLevel": "VERIFIED",
  "isVerified": true
}
```

Allowed `verificationLevel` values:

- `NONE`
- `PENDING`
- `VERIFIED`
- `REJECTED`

Success response:

```json
{
  "success": true,
  "data": {
    "userId": "7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf",
    "verificationLevel": "VERIFIED",
    "isVerified": true
  },
  "timestamp": "2026-05-07T10:00:00Z"
}
```

Typical callers:

- `verification-service` after submit or review

Ownership note:

- `verification-service` decides workflow state
- `user-service` owns the stored verification summary visible on the profile

## Verification Service API

The future `verification-service` owns verification requests, document references, and review workflow.

### Submit verification request

`POST /verification/submit`

Creates a new verification request for the authenticated user using a previously uploaded document key.

Request body:

```json
{
  "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
  "documentKey": "verification/7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf/a1b2c3d4-1111-2222-3333-444455556666/document.pdf"
}
```

Success response:

```json
{
  "success": true,
  "data": {
    "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
    "status": "PENDING",
    "documentKey": "verification/7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf/a1b2c3d4-1111-2222-3333-444455556666/document.pdf",
    "documentOriginalName": "document.pdf",
    "documentContentType": "application/pdf",
    "documentSize": 245812,
    "documentUrl": "verification/7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf/a1b2c3d4-1111-2222-3333-444455556666/document.pdf"
  },
  "timestamp": "2026-05-07T10:00:00Z"
}
```

### Get verification status

`GET /verification/status`

Returns the current verification summary for the authenticated user plus the latest request, if one exists.

Success response:

```json
{
  "success": true,
  "data": {
    "verificationLevel": "PENDING",
    "latestRequest": {
      "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
      "status": "PENDING",
      "documentKey": "verification/7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf/a1b2c3d4-1111-2222-3333-444455556666/document.pdf",
      "documentOriginalName": "document.pdf",
      "documentContentType": "application/pdf",
      "documentSize": 245812,
      "documentUrl": "verification/7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf/a1b2c3d4-1111-2222-3333-444455556666/document.pdf",
      "createdAt": "2026-05-07T10:00:00Z"
    }
  },
  "timestamp": "2026-05-07T10:00:00Z"
}
```

### Review verification request

`POST /verification/review`

Admin-only endpoint that approves or rejects a pending verification request.

Request body:

```json
{
  "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
  "decision": "APPROVED",
  "reviewNotes": "Document is valid and matches profile"
}
```

Allowed `decision` values:

- `APPROVED`
- `REJECTED`

Success response:

```json
{
  "success": true,
  "data": {
    "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
    "status": "APPROVED",
    "verificationLevel": "VERIFIED",
    "reviewNotes": "Document is valid and matches profile",
    "reviewedAt": "2026-05-07T10:05:00Z"
  },
  "timestamp": "2026-05-07T10:05:00Z"
}
```

### List verification requests

`GET /verification/requests`

Admin-only listing endpoint for review operations.

Query params:

- `status` optional: `PENDING`, `APPROVED`, or `REJECTED`
- `page` optional: zero-based page number
- `size` optional: page size

Success response:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
        "userId": "7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf",
        "status": "PENDING",
        "documentKey": "verification/7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf/a1b2c3d4-1111-2222-3333-444455556666/document.pdf",
        "documentOriginalName": "document.pdf",
        "documentContentType": "application/pdf",
        "documentSize": 245812,
        "documentUrl": "verification/7a9ef2d1-8d1c-4d10-8be5-7deebaf1f4bf/a1b2c3d4-1111-2222-3333-444455556666/document.pdf",
        "createdAt": "2026-05-07T10:00:00Z",
        "reviewedAt": null
      }
    ],
    "page": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    }
  },
  "timestamp": "2026-05-07T10:00:00Z"
}
```

## Error Contract

Both future services should keep the current shared error shape:

```json
{
  "timestamp": "2026-05-07T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "documentKey is required",
  "path": "/verification/submit"
}
```

## Transition Plan

Current monolith interfaces already reflect this direction:

- verification state writes go through `UserVerificationClient`
- user lookup goes through `UserLookupClient`

When the services split:

1. keep the interface names stable
2. replace local implementations with HTTP clients
3. keep controllers and verification workflow logic unchanged where possible
