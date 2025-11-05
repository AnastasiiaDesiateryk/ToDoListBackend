## ToDo List Backend — Architecture for Modern Task Management

A **production-ready backend** built with **Spring Boot 3 (Java 21)** and a **clean, layered architecture** designed for scalability, maintainability, and clarity.  
It integrates **Google / Firebase authentication**, issues **stateless JWTs**, and persists multi-user tasks in **PostgreSQL** with **Flyway-managed schema migrations** and **JPA / Hibernate** domain modeling.

---

### Key Highlights

- **Modern Stack** — Spring Boot 3, Java 21, MapStruct, Flyway, PostgreSQL, OpenAPI.  
- **Secure Auth Flow** — Google OAuth(Firebase Auth) → token verification via Nimbus JOSE + JWT → application-scoped JWT or HttpOnly cookie.  
- **Clean Architecture** — Web → Service → Repository layers; immutable DTOs; compile-time mappers.  
- **Data Model** — Enum-driven task states, JSONB metadata, array tagging, optimistic locking.  
- **Observability** — Spring Actuator health endpoints + Swagger UI auto-documentation.  
- **Quality Gates** — Unit & integration testing (JUnit 5, Testcontainers), mutation testing (PIT), coverage (JaCoCo), static analysis (PMD).  
- **DevOps-Ready** — `.env` environment configuration, containerized Postgres, SBOM (CycloneDX), reproducible Maven builds.


If you’d like to learn more about the tests, proceed to the **Quality Assurance** page using the button below.

[![Open QA Page](https://img.shields.io/badge/Quality%20Assurance-Open-blue?style=for-the-badge)](quality_assurance.md)

---

**Purpose:**  
A realistic **enterprise backend skeleton** — production-secure, test-driven, and extensible for multi-tenant SaaS or internal productivity platforms.


### Project Structure

<img width="362" height="867" alt="Screenshot 2025-11-05 at 14 01 08" src="https://github.com/user-attachments/assets/555e663e-bd83-4b19-9021-b4140301928a" />
