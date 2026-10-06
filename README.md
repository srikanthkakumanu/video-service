# Video Service

Video Service maintains video catalog metadata, completed state, and ownership. Uploads, object storage, media streaming, transcoding, and playback are outside the current implementation.
## Technology

Java 27, Spring Boot 4.1.1, Spring Cloud 2025.1.3, Gradle 9.8.0 (Groovy DSL, independent checksum-verified wrapper), MapStruct 1.6.3, Lombok 1.18.48, and springdoc-openapi 3.1.1. Spring Data JPA and Flyway provide PostgreSQL persistence.

These are checked-in versions. The platform's agreed target is Java 27 with a compatible current Spring ecosystem; version upgrades must remain coordinated rather than independently mixing release trains.
## Domain And Relationships

The plain-Java domain represents videos, actors, change commands, queries, and paging. Input/output ports connect transactional application use cases to infrastructure adapters. Infrastructure contains JPA entities/repositories, MapStruct persistence/HTTP mapping, controllers, security, and configuration.

PostgreSQL `videodb` owns this service's records. Keycloak provides JWT identity; Vault/Config Server and Eureka supply configuration/discovery when enabled. Fine-grained Auth Service decisions and gateway video routes are not implemented yet.

Titles are required and limited to 30 characters; descriptions are limited to 100. UUID JWT subjects determine ownership. Authenticated reads expose a shared catalog, not an owner-private list. Ordinary users may mutate only owned videos; `ADMIN` or `MANAGER` may manage/transfer records. Completion changes execute through the transactional application use case.

## Ports

| Endpoint | Shared platform | Local Compose |
| --- | --- | --- |
| API | 9161 | 9161 |
| PostgreSQL published port | 5432 | 35432 |
| Keycloak published port | 8080 | 38080 |

Override with `SERVER_PORT`, `VIDEO_DB_HOST_PORT`, and `VIDEO_KEYCLOAK_HOST_PORT`. Internal dependency ports remain 5432 and 8080. The former API default 9141 was corrected to avoid Auth Service.

## API

Base URL: `http://localhost:9161`.

| Method | Path | Operation |
| --- | --- | --- |
| GET | `/api/videos/ping` | Public connectivity |
| GET | `/api/videos?page=0&size=10` | Paged list |
| GET | `/api/videos/filter` | Filter by optional `id`, `title`, `completed` |
| GET | `/api/videos/{id}` | Read video |
| POST / PUT | `/api/videos` | Create / update using body ID |
| PATCH | `/api/videos/{id}` | Mark completed; no request body |
| DELETE | `/api/videos/{id}` | Delete owned/managed video |

Page sizes are limited to 1..200. Title filtering is case-insensitive and performed in the database. No matches returns an empty list with 200, not 404.

The request record contains `id`, `title`, `description`, `userId`, `userName`, and `completed`. Ordinary callers cannot select another owner. Existing POST/PUT responses retain status 200 and legacy DTO metadata.

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Spring Workshop","description":"Watch later","completed":false}' \
  http://localhost:9161/api/videos
```
## Build And Verification

Run commands from this repository's root; do not use another service's Gradle wrapper.

```bash
bash ./gradlew clean test bootJar
```

The application JAR is written to `build/libs/`. Dockerfiles consume that JAR, so build it before building an image. Set JAVA_HOME to Java 27 to run this repository's Gradle 9.8.0 wrapper, compilation and tests.

The Dockerfile starts `build/libs/video-service-1.0.jar` directly on Temurin 27 as non-root `appuser:appgroup`, with container-aware heap sizing (`MaxRAMPercentage=75`) and curl readiness checks. Its restricted build context contains only the JAR, not local configuration, source or secrets. Set container memory limits and leave room for non-heap memory.

```bash
docker build -t video-service:latest .
ruby bin/verify-image.rb
```

The repeatable smoke requires Docker Desktop (`host.docker.internal`) and Ruby with WEBrick/OpenSSL. It creates an isolated network and temporary PostgreSQL 18 with memory-backed data, initializes `root/root`, `videoadmin/videoadmin` and `theuser/theuser`, and supplies temporary RSA-signed JWTs via actual OIDC/JWKS decoding. Config/Vault imports and Eureka are disabled only for this smoke. Existing databases, volumes and realms are untouched; temporary containers and the network are cleaned up.

Checks cover migrations/table ownership and runtime-role sessions, JWT signature/issuer/expiry rejection, public ping/probes, ADMIN-only JSON/YAML/Swagger/diagnostics, JWT-derived video ownership despite a spoofed body owner, denied cross-user deletion/completion and persisted owner completion. Actual JSON and YAML contracts declare HTTP `bearerAuth` with JWT format and global security requirements. Public ping/probe GET operations explicitly override those requirements; catalog GET retains bearer security. Infrastructure configuration tests cover exact public paths, unchanged non-GET/private operations and absent path documents. The image passes with a read-only root filesystem, writable `/tmp`, dropped capabilities, `no-new-privileges` and a 512 MiB limit.

## Health Probes

Standalone/shared Compose use `/actuator/health/readiness` on port 9161. Readiness includes `readinessState,db` because data APIs require PostgreSQL. Liveness checks application state only. Database failure returns readiness 503 while liveness remains 200; a shared database outage may make all instances unready, so callers/ingress must handle it. Anonymous probes hide components; ADMIN can inspect them.

Pool acquisition defaults to 3000 ms (`SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT`), with 1000 ms validation, to keep ordinary database-unavailable checks within the 5-second probe timeout. Hung network queries still need driver/network timeout policy. Readiness does not prove Config/Vault/Keycloak/Eureka availability.

This increment is not company-platform Keycloak/audience validation, DB recovery, shared configuration/discovery, concurrent-write safety, production load or full-platform acceptance.
## Tests

```bash
bash ./gradlew test
bash ./gradlew integrationTest
```

PostgreSQL integration tests require Docker/Testcontainers. Current verification covers 17 unit/MVC/configuration tests, one PostgreSQL integration test and `bootJar`, plus the separate real container smoke above. Full-platform acceptance remains pending; compatibility warnings and exact evidence are recorded in the checkpoint.
## Run Locally

Choose shared dependencies (PostgreSQL 5432 / Keycloak 8080) or service-local dependencies (35432 / 38080), not both API instances on the same port. The following starts the JVM against service-local dependencies; start PostgreSQL and configure the Keycloak realm first.

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:35432/videodb
export SPRING_DATASOURCE_USERNAME=theuser
export SPRING_DATASOURCE_PASSWORD='<runtime password from local Vault/config>'
export SPRING_FLYWAY_USER=videoadmin
export SPRING_FLYWAY_PASSWORD='<migration password from local Vault/config>'
export KEYCLOAK_ISSUER_URI=http://localhost:38080/realms/company-platform
export SPRING_CLOUD_VAULT_ENABLED=false
export SPRING_CLOUD_CONFIG_ENABLED=false
export EUREKA_CLIENT_ENABLED=false
export SPRING_DOCKER_COMPOSE_ENABLED=false
bash ./gradlew bootRun
```

For externalized configuration, enable the appropriate clients and supply Vault/Config Server endpoints and credentials instead of disabling them. The configured issuer must match the token's `iss` exactly.

The local Compose recipe is:

```bash
docker compose config --quiet
docker compose up -d --build
```

It relies on the sibling `micro-services/postgres-init` initialization scripts. PostgreSQL initialization runs only for a fresh data volume; changing scripts does not repair an existing database automatically.

Flyway V1 is a fresh PostgreSQL schema baseline, not an in-place migration of an old MariaDB volume. Use runtime account `theuser` and migration account `videoadmin` with the same credentials specified in local Vault/configuration across instances.

## Operations And Remaining Work

Check GET `/actuator/health` and the public ping endpoint for connectivity. OpenAPI is configured at `/api-docs` and Swagger UI at `/swagger-ui.html`; documentation and non-health Actuator endpoints require ADMIN. Health details are shown only to authorized administrators. A healthy process alone does not verify issuer alignment, database permissions, or the end-to-end gateway path.

Fine-grained Auth Service enforcement, cross-service lifecycle events, optimistic concurrency, production deployment hardening, and full-platform smoke verification remain pending. Existing resource representations are retained for compatibility.

See [platform orchestration](../micro-services/README.md), [configuration](../service-configs/README.md), and the [implementation checkpoint](../micro-services/IAM_IMPLEMENTATION_CHECKPOINT.md).

## Architecture Reference

```text
video-service
  domain/model                Video, VideoActor, VideoChanges, VideoQuery, VideoPage
  domain/port/in              VideoCatalog use-case API
  domain/port/out             VideoStore persistence contract
  application/usecase         VideoCatalogService transactional business operations
  infrastructure/web          VideosController, DTOs, facade, MapStruct mapper
  infrastructure/persistence  JPA entity/repository, store adapter, persistence mapper
  infrastructure/security     JWT resource-server and current actor extraction
```

Request flow:

```text
HTTP /api/videos...
  -> SecurityConfig validates Keycloak JWT except GET public ping/health; docs/diagnostics require ADMIN
  -> VideosController maps DTOs
  -> VideoService facade resolves VideoActor
  -> VideoCatalogService applies ownership and manager/admin rules
  -> JpaVideoStore persists to tbl_video
```

## Configuration Reference

| Variable | Default / role |
| --- | --- |
| `SERVER_PORT` | `9161` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/videodb` unless overridden |
| `user` / `password` | Runtime DB credentials from Vault-style placeholders; local fallback `theuser` |
| `flw-user` / `flw-password` | Flyway credentials; local fallback `videoadmin` |
| `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` | Pool acquisition timeout in milliseconds, default `3000` |
| `KEYCLOAK_ISSUER_URI` / `issuer-uri` | JWT issuer; must match token `iss` |
| `VAULT_HOST`, `VAULT_PORT`, `VAULT_TOKEN` | Vault integration |
| `EUREKA_CLIENT_SERVICE_URL_DEFAULT_ZONE` | Registry URL |
| `ROOT_LOG_LEVEL`, `SPRING_SECURITY_LOG_LEVEL` | Logging verbosity |

## Command Reference

| Task | Command |
| --- | --- |
| Unit/MVC tests | `bash ./gradlew test` |
| PostgreSQL integration tests | `bash ./gradlew integrationTest` |
| Build executable JAR | `bash ./gradlew bootJar` |
| Full verification | `bash ./gradlew clean test integrationTest bootJar` |
| Build image | `docker build -t video-service:latest .` |
| Health | `curl http://localhost:9161/actuator/health` |
| Public ping | `curl http://localhost:9161/api/videos/ping` |

Representative protected request:

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://localhost:9161/api/videos/filter?title=Spring&page=0&size=20"
```

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `401` | Token missing, expired, or issued by a different issuer URI. |
| `403` | Caller is not owner/admin/manager for the requested mutation. |
| `404` | Requested video ID does not exist. |
| Duplicate title failure | `tbl_video.title` is unique. |
| Startup fails before DB access | Vault/Config import is unreachable; disable clients for local standalone runs or start dependencies. |


## JWT Audience Contract

Spring Boot's managed JWT decoder requires the configured issuer and the audience `company-platform-api`. Override the audience with `KEYCLOAK_API_AUDIENCE` when running locally against a different API client. A correctly signed token with a missing or different `aud` claim returns HTTP 401; realm roles do not bypass audience validation.

`ruby bin/verify-image.rb` checks the actual packaged application's RSA/JWKS decoder with accepted, missing and incorrect audiences, while retaining catalog, ownership, documentation and probe checks. These checks use a controlled local issuer, not genuine Keycloak realm acceptance.

## Repository CI

[Build workflow](.github/workflows/build.yml) runs independently on pushes, pull requests and manual dispatch with Temurin Java 27 on Ubuntu 24.04. It verifies this repository's wrapper JAR and Gradle distribution checksum, runs `check integrationTest bootJar` with fresh tasks and Gradle deprecations treated as failures, builds the service image and retains test reports for seven days. Actions are pinned to verified commit SHAs; permissions are read-only and checkout credentials are not persisted.

PostgreSQL integration tests use Docker/Testcontainers; no production database or platform credentials are required. The workflow definition passes local actionlint/structural checks, but has not run on GitHub while these changes remain uncommitted/unpushed. Image building in CI does not replace the separate runtime/probe smoke evidence recorded in the checkpoint.
