# TrustPlatform

TrustPlatform is a cloud-deployed identity verification backend designed around a realistic document-review workflow: users register, upload verification documents, submit review requests, and admins inspect private files through short-lived presigned links instead of public URLs.

The project matters because it moves beyond a toy auth API into the kinds of concerns that show up in real backend systems: secure file handling, cloud configuration, private object storage, role-based access control, operational logging, readiness checks, and environment-driven deployment.

The current implementation is built with Spring Boot and deployed on AWS using Elastic Beanstalk, RDS PostgreSQL, and private S3 storage.

At this stage, the repository contains one deployable service:

- `auth-service` — JWT auth, user profiles, verification submission, private S3 uploads, admin request listing, and presigned document-link generation

## Tech Stack

- **Backend:** Java 21, Spring Boot
- **Security:** Spring Security, JWT
- **Database:** PostgreSQL, Spring Data JPA, Hibernate
- **File Storage:** Amazon S3 with presigned URL access
- **Cloud Deployment:** AWS Elastic Beanstalk
- **Cloud Database:** Amazon RDS PostgreSQL
- **Build Tool:** Maven
- **Testing:** JUnit, Spring Boot integration tests
- **API Testing:** Postman, curl

## Repository Layout

```text
trustplatform/
└── auth-service/
    ├── src/main/java/...
    ├── src/main/resources/...
    ├── src/test/java/...
    ├── postman/
    ├── pom.xml
    └── README.md
```

## Current Architecture

```text
Client / Postman / curl
          |
          v
AWS Elastic Beanstalk
  └── auth-service (Spring Boot, Java 21)
          |
          +--> Amazon RDS PostgreSQL
          |
          +--> Amazon S3 (private verification documents)
                     |
                     +--> Presigned GET URLs for admin review
```

## What The Project Does

- Registers and authenticates users with JWTs
- Stores user verification state in PostgreSQL
- Uploads private verification documents to S3
- Stores S3 object keys and metadata instead of public file URLs
- Lets admins list verification requests
- Generates short-lived presigned S3 URLs for private document review
- Tracks important actions in an audit log

## Deployment Status

The backend has been deployed to AWS Elastic Beanstalk and connected to:

- Amazon RDS PostgreSQL
- Amazon S3 private bucket storage

Deployed base URL:

```text
http://trustplatform-dev.eba-ihrcejd2.us-east-2.elasticbeanstalk.com
```

Confirmed deployed subset:

- `/health`, `/live`, `/ready`
- `/auth/signup`
- `/auth/login`
- `/auth/me`
- `/files/upload`
- `/verification/submit`
- admin request listing
- admin presigned document-link generation

## Local Development

From the service directory:

```bash
cd auth-service
./mvnw spring-boot:run
```

Package the deployable jar:

```bash
cd auth-service
./mvnw clean package
```

Run tests:

```bash
cd auth-service
./mvnw test
```

## Documentation

The detailed service documentation lives in:

- [auth-service/README.md](./auth-service/README.md)

That README includes:

- environment variables
- AWS and database setup
- API endpoints
- curl examples
- verification flow rules
- deployment notes
- architecture summary

## Week 5 Milestone

Week 5 established the cloud deployment baseline:

- Spring Boot service deployed to Elastic Beanstalk
- PostgreSQL running on RDS
- private file storage working in S3
- verification submission flow working in the deployed environment
- diagnostics, readiness checks, and bootstrap-admin support added

The next step is to finish and validate the full deployed admin approval flow end to end.
