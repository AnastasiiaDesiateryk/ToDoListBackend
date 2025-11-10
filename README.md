# Todo App

Production-ready Spring Boot backend for a TODO application with:
- PostgreSQL + Flyway migrations (tags as text[], metadata as jsonb)
- JWT authentication (Nimbus via spring-security-oauth2-jose)
- Google ID token verification (JWKs)
- ETag / If-Match optimistic locking using JPA @Version
- MapStruct for DTO mapping
- Hibernate Types for arrays and JSON
- OpenAPI (springdoc)
- Dockerfile for build + runtime

Features
- User sign-in via Google ID token -> issues JWT
- CRUD tasks with ownership and sharing (viewer/editor)
- Search & filters (native and dynamic queries)
- ETag support for PATCH semantics
- AI interpret endpoint (rule-based stub)
- Actuator health endpoints

Prerequisites
- Java 21 (Temurin/OpenJDK)
- Maven 3.8+
- PostgreSQL (for local run) or Testcontainers for tests
- Docker (to build container)

Installation
1. Clone repository
2. Create database and user, or use Docker Compose / local Postgres.

Build
mvn -DskipTests package

Run (local)
Set env (example in `.env.example`) then:
java -jar target/*.jar

Or with Maven
mvn spring-boot:run

Docker
docker build -t todo-app .
docker run -e DB_URL=jdbc:postgresql://host:5432/todo -e DB_USER=user -e DB_PASSWORD=pass -e JWT_SECRET=yoursecret -p 8080:8080 todo-app

Configuration
- application.yml reads env variables:
  - DB_URL, DB_USER, DB_PASSWORD
  - JWT_SECRET (recommend base64 32+ bytes)
  - GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
- cors.allowed-origins to configure allowed origins

Important Implementation Notes
- pom.xml uses spring-boot-starter-parent 3.3.x to manage versions.
- MapStruct version is set to 1.6.x; maven-compiler-plugin configured to run annotation processor.
- Hibernate types (hibernate-types-60) used with fully-qualified @Type FQCN to map text[] and jsonb.
- Flyway V1 migration creates enums, tables, GIN indexes and updated_at trigger.
- SecurityConfig registers JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter and exposes actuator health endpoints.
- ETagUtil formats and parses weak ETags like W/"<version>".
- GoogleTokenVerifier caches Google's JWK set for 1 hour.
- TaskService enforces ACLs and optimistic locking; patch respects If-Match header.

Testing
- Unit & integration tests with Spring Boot Test and Testcontainers.
- Run tests:
mvn test

Project Structure
- src/main/java/com/example/todo
  - config: Security configuration
  - controller/web: REST controllers
  - dto: request/response DTOs
  - entity: JPA entities (AppUser, Task, TaskShare, enums)
  - mapper: MapStruct mapper
  - repository: Spring Data repositories + custom impl
  - security: JWT, Google verifier, auth filter, principal
  - service: business logic (UserService, TaskService)
  - util: ETag utility
  - health: liveness/readiness indicators
- src/main/resources/db/migration: Flyway migrations
- Dockerfile: Multi-stage build (maven -> runtime)

Troubleshooting
- Flyway validate fails: ensure DB schema has been created by Flyway; check Spring datasource points to correct DB.
- JWT issues: ensure JWT_SECRET is set and has sufficient entropy; mismatch issuer leads to verification failure.
- Google sign-in fails: set GOOGLE_CLIENT_ID to verify audience.
- MapStruct generated classes missing: ensure maven-compiler-plugin has annotationProcessorPaths and build is done with mvn package.
- CORS errors: set cors.allowed-origins appropriately.

Support
This project is a template focused on best practices: secure JWT handling, DB types mapping, ETag semantics and tests. Customize business rules and UI integration as needed.
