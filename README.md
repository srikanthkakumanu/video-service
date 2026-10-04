# video-service

Video catalog metadata and completion APIs at `/api/videos`, served on port 9141. Uses Java 27, Spring Boot 4.1.1, Spring Cloud 2025.1.3, PostgreSQL/Flyway, MapStruct 1.6.3, and Keycloak resource-server security.

## Build And Run

Use this service's independent Gradle wrapper:

```bash
./gradlew test
./gradlew integrationTest
./gradlew bootJar
docker compose up -d postgres keycloak
./gradlew bootRun
```

The integration tests require Docker and use an isolated PostgreSQL 18 database. When launching Gradle 8.14.3, use a Java 21 JAVA_HOME if Java 27 is unsupported by the wrapper runtime; the Java 27 project toolchain still compiles and runs tests.

The Keycloak `company-platform` realm must be provisioned before JWT validation works. Default database credentials match the shared Vault convention: runtime `theuser/theuser`, migrations `videoadmin/videoadmin`. Override `SPRING_DATASOURCE_URL`, `KEYCLOAK_ISSUER_URI`, Vault settings and `SERVER_PORT` for your environment.

## APIs And Authorization

Authenticated users may read video metadata. Creating a video uses the JWT subject as owner for regular users. Updating, deleting and completing a video require ownership, ADMIN or MANAGER. Only managers may assign or transfer ownership. Completion is performed as a single transactional application use case.

Search: `/api/videos/filter?title=Example&completed=true`. Listing supports `page` and `size` (1 to 200). OpenAPI: `/api-docs`. Swagger: `/swagger-ui.html`. Health: `/actuator/health`. Send `Authorization: Bearer <token>` to protected endpoints.

The framework-free domain and ports live under `domain`; transactions under `application/usecase`; JPA, HTTP compatibility DTO facades, mappers and security under `infrastructure`. Filters are applied in PostgreSQL rather than by loading the whole table into memory. Domain timestamps are UTC instants.

See `../IAM_IMPLEMENTATION_CHECKPOINT.md` for the full workspace migration state.
