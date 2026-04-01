# TrustPlatform Auth Service

A Spring Boot authentication service with JWT-based security, user profiles, and audit logging.

## Tech Stack

- Java 21, Spring Boot 3.5.13
- PostgreSQL 17
- Spring Security (stateless JWT)
- Spring Data JPA / Hibernate
- JJWT 0.12.6
- Lombok

## Prerequisites

- Java 21+
- PostgreSQL 17+
- Maven (or use the included `./mvnw` wrapper)

## Database Setup

```bash
# Start PostgreSQL (Homebrew)
brew services start postgresql@17

# Create the database
createdb -U <your_username> authdb

# Verify connection
psql -U <your_username> -d authdb -c "SELECT 1;"
```

## Run the App

```bash
cd auth-service

# Compile
./mvnw compile

# Run
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`.

## API Endpoints

### POST /auth/signup

Register a new user.

**Request:**
```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

**Response (200):**
```
User created
```

**Response (400) — duplicate email:**
```
Email already exists
```

---

### POST /auth/login

Login and receive a JWT token.

**Request:**
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (401) — invalid credentials:**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password"
}
```

---

### GET /auth/me

Get the current user's info. Requires a valid JWT token.

**Request:**
```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer <accessToken>"
```

**Response (200):**
```json
{
  "userId": "7082ca1d-19ae-49c5-b4b4-d6c6427d3515",
  "email": "test@example.com",
  "isVerified": false,
  "verificationLevel": "NONE"
}
```

**Response (403) — no token or invalid token:**
```
403 Forbidden
```

---

## Seed Test Flow

Follow this exact sequence to test the full authentication flow:

### Step 1: Register a new user

```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "seed@example.com",
    "password": "password123"
  }'
```

Expected: `User created`

### Step 2: Login to get a token

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "seed@example.com",
    "password": "password123"
  }'
```

Expected: `{"accessToken":"eyJhbG..."}`

### Step 3: Copy the token

Copy the `accessToken` value from the login response.

### Step 4: Access the protected endpoint

```bash
curl -X GET http://localhost:8080/auth/me \
  -H "Authorization: Bearer <paste accessToken here>"
```

Expected:
```json
{
  "userId": "...",
  "email": "seed@example.com",
  "isVerified": false,
  "verificationLevel": "NONE"
}
```

### Verify in the database

```bash
psql -U <your_username> -d authdb -c "SELECT * FROM users;"
psql -U <your_username> -d authdb -c "SELECT * FROM user_profile;"
psql -U <your_username> -d authdb -c "SELECT * FROM audit_log;"
```

You should see matching rows in all three tables.
