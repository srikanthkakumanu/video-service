# video-service

The video context of the platform: **videos, whether they are completed, and who owns them**. It stores metadata only; uploads, storage, streaming and playback are not part of it.

It is a business service on the identity platform and relies on that platform for everything about people:

- **Users** are the ones [`user-service`](../user-service/README.md) manages. This service stores no user data; a video only records the platform user ID of its owner.
- **Login** happens at [`auth-service`](../auth-service/README.md). Nothing here is usable without a platform access token.
- **Roles** are created and assigned in `auth-service`. This service defines none and checks only the permissions that arrive in the token.

It is built the same way as [`books-service`](../books-service/README.md).

## Contents

- [Who may do what](#who-may-do-what)
- [API](#api)
- [Errors](#errors)
- [Rules the service enforces](#rules-the-service-enforces)
- [Architecture](#architecture)
- [Data](#data)
- [Seed data](#seed-data)
- [Configuration](#configuration)
- [Dev users and passwords](#dev-users-and-passwords)
- [Run](#run)
- [Test](#test)
- [Build and image](#build-and-image)

## Who may do what

Being logged in is not enough: a caller needs a video role. A user with only the platform's default `USER` role gets `403`, and so does a user who only has a role for the book catalog.

| Role (managed in auth-service) | Permissions in the token | May |
| --- | --- | --- |
| `VIDEO_READER` | `videos:read` | read videos |
| `VIDEO_EDITOR` | `videos:read`, `videos:write` | also add videos, and change, complete or remove their own |
| `VIDEO_MANAGER` | `videos:read`, `videos:write`, `videos:manage` | also change or remove any video and transfer ownership |
| `PLATFORM_ADMIN` | all three | everything |

Give someone a role through the platform, never here:

```bash
curl -s -X POST localhost:9211/api/v1/users/$USER_ID/roles -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d '{"roles":[{"name":"VIDEO_EDITOR"}]}'
```

The user's next token (after a refresh or a new login) carries the permissions. The roles, the permissions and the `video-service` client itself are created through the platform APIs by the onboarding job in [`micro-services`](../micro-services/README.md) (`onboarding/video-service.json`).

## API

Base path `/api/v1/videos`. Reach it through the gateway (`http://localhost:9211`); the service itself listens on 9161. OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html` (off in `prod`).

Every call needs `Authorization: Bearer <access token>` from `POST /api/v1/auth/login`.

| Method and path | Purpose | Needs |
| --- | --- | --- |
| `GET /api/v1/videos` | Search. Filters: `title` (contains, any case), `completed`, `ownerId`, `owner=me`. Paging: `page`, `size`, `sort`. | `videos:read` |
| `GET /api/v1/videos/{id}` | Read a video | `videos:read` |
| `POST /api/v1/videos` | Add a video. `201` with `Location`. It belongs to the caller; a manager may name another `ownerId`. | `videos:write` |
| `PUT /api/v1/videos/{id}` | Replace its details | `videos:write`, and owner or `videos:manage` |
| `POST /api/v1/videos/{id}/complete` | Mark it completed. No body. | `videos:write`, and owner or `videos:manage` |
| `PUT /api/v1/videos/{id}/owner` | Give it to another active platform user: `{"ownerId": "..."}` | `videos:manage` |
| `DELETE /api/v1/videos/{id}` | Remove it. `204`. | `videos:write`, and owner or `videos:manage` |

```json
// POST or PUT /api/v1/videos
{"title": "Spring Boot in One Hour", "description": "From an empty folder to a running REST service.", "completed": false}

// response
{"id": "...", "title": "Spring Boot in One Hour", "description": "From an empty folder to a running REST service.",
 "ownerId": "<platform user id or null>", "completed": false, "createdAt": "...", "updatedAt": "..."}
```

Lists return `items`, `total`, `page`, `size`. `page` starts at 0; `size` is 1 to 100 (default 20). `sort` is `field` or `field,asc|desc`, by `title` (default) or `createdAt`.

The previous paths (`/api/videos`, `/api/videos/filter`, `PATCH /api/videos/{id}`, `/api/videos/ping`) are gone.

```bash
. ../micro-services/.env
TOKEN=$(curl -s -X POST localhost:9211/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"platform-admin\",\"password\":\"$PLATFORM_ADMIN_PASSWORD\"}" | jq -r .accessToken)

curl -s "localhost:9211/api/v1/videos?size=3&sort=title" -H "Authorization: Bearer $TOKEN" | jq
```

## Errors

Every error is an RFC 9457 problem (`application/problem+json`) with a stable `type` (`https://platform.local/problems/<code>`) and a matching `code`. Clients should switch on `code`, never on the text.

| `code` | Status | When |
| --- | --- | --- |
| `invalid-value` | 400 | Validation failed; `errors` lists `field` and `message` |
| `unauthorized` | 401 | No token, or one that cannot be verified or is not meant for this service |
| `forbidden` | 403 | The token lacks the permission |
| `operation-not-permitted` | 403 | A rule refused it, for example changing someone else's video |
| `video-not-found` | 404 | |
| `duplicate-video` | 409 | Another video already has the title |
| `owner-not-eligible` | 422 | The user a video is to be given to does not exist or is not active |
| `user-directory-unavailable` | 503 | user-service or auth-service could not be asked about a user |
| `internal-error` | 500 | Anything unexpected |

## Rules the service enforces

Domain rules, tested without Spring:

- **A title is 1 to 30 characters and no two videos share one.** A description is at most 100 characters. These limits are the ones the service had before.
- **A video belongs to whoever adds it.** Only a video manager may add a video for someone else.
- **Only the owner or a video manager changes, completes or removes a video.** Completing a completed video changes nothing.
- **Videos without an owner** (the starter set) are changed only by a video manager.
- **Only a video manager transfers a video**, even the owner cannot, and only to a user that user-service reports as active.

Things to know:

- Ordinary requests need no call to another service: identity and permissions come from the token. Only giving a video to another user asks user-service.
- A revoked role or a logout takes effect here when the access token expires or is refreshed (about five minutes in dev).
- When a user is deleted in user-service their videos keep the old owner ID; a video manager can reassign or remove them.

## Architecture

Clean architecture; dependencies point inward and ArchUnit fails the build if they do not.

```
com.videos
├── domain            Pure Java. No Spring, JPA or HTTP.
│   ├── model         Video, VideoDetails, VideoId, OwnerId, Title, VideoActor, VideoSearch, Paging, PageResult
│   ├── port          VideoRepository, UserDirectoryPort
│   └── exception     One type per error code
├── application       One class per use case: CreateVideo, UpdateVideo, CompleteVideo, TransferVideo,
│                     DeleteVideo, GetVideo, SearchVideos, SeedVideos. Depends only on domain.
├── infrastructure
│   ├── persistence   JPA entity and repository
│   ├── platform      The only code that calls user-service and auth-service
│   ├── seed          Reads the seed file and runs SeedVideos at startup
│   └── config        Wires the use cases as beans
└── interfaces
    ├── rest          Controller, request and response models, problem-detail error handling
    └── security      Filter chain, permission expressions, the caller as the domain sees them
```

- The domain's view of the caller, `VideoActor`, is built from the validated token: the user ID from `sub`, and whether they manage every video from the `videos:manage` permission.
- `UserDirectoryPort` is the domain's only knowledge of users. Its adapter obtains a client-credentials token from auth-service, calls `GET /api/v1/users/{id}` on user-service through the registry, caches the token until shortly before it expires, and gets a new one once if it is refused.
- Tokens are validated with the shared `platform-security-starter` from `micro-services`: signature against the JWKS, RS256 only, exact issuer, `video-service` in the audience, expiry with clock skew, `typ` `Bearer`.

## Data

Database `videodb` in the platform's Postgres. Flyway owns the schema (`src/main/resources/db/migration`); Hibernate only validates it.

| Table | Columns |
| --- | --- |
| `video` | `id`, `title` (unique), `description` (optional), `owner_id` (platform user ID, or null), `completed`, `created_at`, `updated_at`, `version` |

| Account | Role | Used for |
| --- | --- | --- |
| `videoadmin` | owns the schema | Flyway migrations at startup |
| `theuser` | reads and writes rows; cannot create, alter or drop tables | everything else |

The database and both accounts are created by the platform's database job with credentials read from Vault.

## Seed data

`src/main/resources/data/videos.json` holds a starter set of 20 sample videos (a fixed ID, a title and a description each). The service had no seed data before; this set was written for it.

- Entries go through the same domain objects as API requests, so a seed file that breaks a rule stops the service at startup instead of loading bad data.
- Loading is safe to repeat: an entry whose ID is already stored is left alone, so later edits survive a restart.
- Seeded videos have no owner; only a video manager can change them.
- It runs when `videos.seed.enabled` is `true`: on in `dev`, off in `qa` and `prod` (set in `service-configs`).

## Configuration

Split by environment ([ADR 0014](../micro-services/docs/adr/0014-environment-profiles.md)): `application.yml` holds what is common; `application-dev.yml`, `-qa.yml` and `-prod.yml` hold the Config Server and Vault imports, optional with localhost defaults in `dev` and required elsewhere. More settings come from the Config Server (`service-configs/application*.yml` and `video-service*.yml`).

| Variable | Default in `dev` | Meaning |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev`, `qa` or `prod` |
| `SERVER_PORT` | `9161` | HTTP port |
| `CONFIG_SERVER_URL` | `http://localhost:9311` | Config Server |
| `VAULT_URI`, `VAULT_TOKEN` | `http://localhost:8200`, none | Vault and this service's token |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/videodb` | Its database |
| `KEYCLOAK_URL` | `http://localhost:8080` | Where it fetches signing keys |
| `KEYCLOAK_PUBLIC_URL` | `http://localhost:8080` | The issuer in tokens; compared exactly |
| `EUREKA_URL` | `http://localhost:9111/eureka/` | Registry |

Other settings: `videos.seed.enabled`; `platform.directory.user-service-url` and `auth-service-url` (service names resolved through the registry by default), `platform.directory.load-balanced`, `connect-timeout`, `read-timeout`.

No credential is in any source or configuration file of this service. From Vault:

| Vault path | Keys | Written by |
| --- | --- | --- |
| `secret/video-service` | `spring.datasource.username`, `spring.datasource.password` (runtime account), `spring.flyway.user`, `spring.flyway.password` (schema admin) | the platform's Vault seeding job |
| `secret/clients/video-service` | `client-secret` (for its own service token) | auth-service, when the client is registered or its secret renewed |

## Dev users and passwords

For the development environment only.

| Account | User | Password | Where it is kept |
| --- | --- | --- | --- |
| `videodb` schema admin | `videoadmin` | `videoadmin` | Vault `secret/video-service` (`spring.flyway.*`) |
| `videodb` runtime | `theuser` | `theuser` | Vault `secret/video-service` (`spring.datasource.*`) |
| Vault dev root token | | `srikanth` | `micro-services/.env.dev.example` |
| Platform administrator (holds every video permission) | `platform-admin` | generated; `PLATFORM_ADMIN_PASSWORD` in `micro-services/.env` | Vault `secret/keycloak` |
| This service's Vault token | | generated; `VIDEO_SERVICE_VAULT_TOKEN` in `micro-services/.env` | |
| This service's client secret | `video-service` | generated by auth-service | Vault `secret/clients/video-service` |

There are no application users of this service's own: sign in with a platform user. The full table for the platform is in the [`micro-services` README](../micro-services/README.md#dev-users-and-passwords).

## Run

This repository must sit next to [`micro-services`](../micro-services/README.md), which holds the version catalog and the shared starter.

**With the whole platform** (the usual way): `cd ../micro-services && make up`. video-service starts last, with books-service: after the gateway is up and the onboarding job has registered it with the platform.

**From source, against the running platform:** `cd ../micro-services && scripts/run-from-source.sh video-service`

**Restart just this service** after a change: `cd ../micro-services && scripts/restart.sh --build video-service`

The service shuts down gracefully: on stop it finishes requests in flight (up to 30 seconds) and deregisters from Eureka.

## Test

```bash
./gradlew build
```

Needs Docker for Testcontainers. 70 tests; none are skipped.

| Kind | Tests | Against |
| --- | --- | --- |
| Domain | 16 | Plain Java: value objects, ownership and completion rules |
| Use cases | 12 | In-memory ports, including the seed loader |
| Architecture (ArchUnit) | 7 rules | The compiled classes |
| Repository | 7 | Postgres 18, schema from Flyway: constraints, search, paging |
| User lookup adapter | 10 | A stand-in for auth-service and user-service over HTTP |
| Controller (`@WebMvcTest`) | 13 | Mocked use cases: validation, error mapping, authorization |
| Whole service | 5 | Postgres, the real seed file, tokens verified against a JWKS endpoint |

The build fails if line coverage of `domain` and `application` drops below 80% (currently 100%).

The end-to-end tests are in `micro-services` (`VideosE2ETest`, run with `make test-e2e`): users are created through user-service, roles assigned through auth-service, and videos are used through the gateway.

## Build and image

- Java 27, Gradle 9.8.0 (wrapper), Spring Boot 4.1.1. Versions come from `../micro-services/gradle/libs.versions.toml`.
- `Dockerfile` is multi-stage: build on JDK 27, run on a JRE 27 Alpine image as a non-root user, with a health check on `/actuator/health/readiness`. It needs the platform root as a named build context, which `micro-services/docker-compose.yml` supplies:

```bash
docker build --build-context platform=../micro-services -t video-service .
```

- CI (`.github/workflows/build.yml`) checks out `micro-services` next to this repository, runs `./gradlew build` and builds the image.
- `bin/verify-image.rb` is a smoke test written for the previous implementation (its own Keycloak realm, `/api/videos/ping`, fixed passwords). It does not match this service any more and is not run by CI.
