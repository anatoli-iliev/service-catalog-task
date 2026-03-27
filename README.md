# Software Catalog Service

A Spring Boot backend service that tracks software applications and their releases via asynchronous Kafka events, persisting state to PostgreSQL and exposing a RESTful API.

## Tech Stack

- **Java 25**, **Spring Boot 4.0**, **Gradle 9.4**
- **PostgreSQL 15** with **Flyway** migrations
- **Apache Kafka 4.2** (KRaft mode, official Apache image)
- **Micrometer** + **Prometheus** for metrics, **Spring Boot Actuator** for health checks
- **Structured JSON logging** via logstash-logback-encoder with correlation IDs
- **OpenAPI 3** (Swagger UI)
- **Virtual threads** enabled for improved scalability

## Quick Start

### Option 1: Full Stack via Docker Compose

```bash
docker-compose up -d
```

This starts PostgreSQL, Kafka, and the application. The API is available at `http://localhost:8080`.

### Option 2: Local Development

Start infrastructure:

```bash
docker-compose up -d postgres kafka
```

Run the application:

```bash
./gradlew bootRun
```

For human-readable logs (instead of JSON):

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Build

```bash
./gradlew clean build -x test
```

### Run Tests

Integration tests require PostgreSQL. E2E tests use an embedded Kafka broker (no Docker needed for Kafka).

```bash
docker-compose up -d postgres
./gradlew test
```

**91 tests, 95% line coverage** across unit, slice, integration, and E2E test categories.

## API Documentation

With the application running, visit:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI spec**: http://localhost:8080/api-docs

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/applications?page=0&size=20` | List applications (paginated, alphabetical) |
| GET | `/api/v1/applications/{id}` | Get application by external ID |
| GET | `/api/v1/applications/{id}/releases?page=0&size=20` | List releases (paginated, SemVer descending) |
| GET | `/api/v1/releases/{id}` | Get release by external ID |

## Verification

After starting the full stack, publish test events to Kafka:

```bash
# 1. Send a release BEFORE its application (tests ghost record pattern)
echo '{"releaseId":"rel-1","applicationId":"app-1","version":"1.0.0","ociReference":"registry.io/app:1.0.0","releaseDate":"2026-01-15T10:00:00Z"}' | docker exec -i catalog-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic catalog.releases

# 2. Verify ghost application was created
curl -s http://localhost:8080/api/v1/applications/app-1 | jq .

# Expected:
{
  "applicationId": "app-1",
  "name": null,
  "description": null,
  "repositoryUrl": null
}

# 3. Send the application event (promotes ghost to real)
echo '{"applicationId":"app-1","name":"postgresql","description":"Relational Database","repositoryUrl":"https://github.com/bitnami/containers/postgresql"}' | docker exec -i catalog-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic catalog.applications

# 4. Verify ghost was promoted
curl -s http://localhost:8080/api/v1/applications/app-1 | jq .

# Expected:
{
  "applicationId": "app-1",
  "name": "postgresql",
  "description": "Relational Database",
  "repositoryUrl": "https://github.com/bitnami/containers/postgresql"
}

# 5. Send more releases to verify SemVer sorting
echo '{"releaseId":"rel-2.1.0","applicationId":"app-1","version":"2.1.0","ociReference":"registry.io/app:2.1.0","releaseDate":"2026-01-15T10:00:00Z"}' | docker exec -i catalog-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic catalog.releases
echo '{"releaseId":"rel-1.10.0","applicationId":"app-1","version":"1.10.0","ociReference":"registry.io/app:1.10.0","releaseDate":"2026-01-15T10:00:00Z"}' | docker exec -i catalog-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic catalog.releases
echo '{"releaseId":"rel-1.9.0","applicationId":"app-1","version":"1.9.0","ociReference":"registry.io/app:1.9.0","releaseDate":"2026-01-15T10:00:00Z"}' | docker exec -i catalog-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic catalog.releases
echo '{"releaseId":"rel-1.9.1-beta.1","applicationId":"app-1","version":"1.9.1-beta.1","ociReference":"registry.io/app:1.9.0","releaseDate":"2026-01-15T10:00:00Z"}' | docker exec -i catalog-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic catalog.releases

# 6. Verify SemVer sort order: 2.1.0 > 1.10.0 > 1.9.1-beta.1 > 1.9.0 > 1.0.0
curl -s "http://localhost:8080/api/v1/applications/app-1/releases" | jq '.content[].version'

# Expected:
"2.1.0"
"1.10.0"
"1.9.1-beta.1"
"1.9.0"
"1.0.0"
```

## Architecture Decisions

### Internal vs External Identifiers

External IDs from Kafka (`applicationId`, `releaseId`) are stored in dedicated columns with UNIQUE constraints but are **never used as primary keys**. Internal auto-incrementing `BIGSERIAL` IDs serve as PKs and foreign keys. The REST API exposes only external IDs, keeping internal database concerns hidden.

### Semantic Version Sorting (with Pre-release Support)

Versions are decomposed into integer columns (`version_major`, `version_minor`, `version_patch`) plus nullable `version_prerelease` and `version_prerelease_sort_key` columns. Pre-release labels (e.g., `1.0.0-beta.1`) follow SemVer 2.0.0 precedence rules: stable releases sort higher than pre-releases, and pre-release identifiers are compared left-to-right (numeric as integers, alphanumeric as strings). A normalized sort key enables correct lexicographic ordering in SQL. Build metadata (e.g., `+build.123`) is parsed but ignored per the SemVer spec. Leading zeros in version components are rejected per the spec.

### Ghost Record Pattern (Eventual Consistency)

Kafka topics are unordered. A release event may arrive before its application event. When this happens, the service creates a "ghost" application record with only the external ID (all other fields null). When the real application event arrives, an `INSERT ... ON CONFLICT DO UPDATE SET name = COALESCE(EXCLUDED.name, applications.name)` upsert promotes the ghost to a full record. The `COALESCE` logic ensures a late-arriving ghost upsert (from another release) never overwrites real data.

### Idempotent Event Processing

All Kafka-driven writes use PostgreSQL `ON CONFLICT ... DO UPDATE`, making them safe against at-least-once delivery. Duplicate messages are handled gracefully without exceptions.

### JdbcTemplate for Upserts

Native PostgreSQL `INSERT ... ON CONFLICT ... RETURNING id` queries use `JdbcTemplate` rather than Spring Data `@Modifying` queries, because `@Modifying` does not reliably support `RETURNING` clauses.

### `open-in-view: false`

Disabled to prevent the Open Session in View anti-pattern, which holds database connections for the entire HTTP request lifecycle. Lazy-loaded relationships are fetched explicitly via `JOIN FETCH` in repository queries.

## Observability

### Health Checks

```
GET /actuator/health
```

Returns aggregate health status. Components checked:
- **db** (auto-configured): PostgreSQL connectivity via HikariCP
- **kafka** (auto-configured): Kafka broker connectivity
- **kafkaConsumerLag** (custom): Consumer group lag across all partitions. Reports DOWN when total lag exceeds 1000.

### Metrics

```
GET /actuator/prometheus
```

Exposes all metrics in Prometheus exposition format. Key metrics:

| Metric | Type | Description |
|--------|------|-------------|
| `catalog_events_processed_total` | Counter | Kafka events processed, tagged by `topic` and `status` (success/failure) |
| `http_server_requests_seconds` | Timer | HTTP request duration (auto-configured by Micrometer) |
| `jvm_memory_used_bytes` | Gauge | JVM memory usage (auto-configured) |
| `hikaricp_connections_active` | Gauge | Active database connections (auto-configured) |
| `kafka_consumer_records_consumed_total` | Counter | Raw Kafka records consumed (auto-configured) |

### Structured Logging

Logs are emitted as JSON (via logstash-logback-encoder) with MDC fields:
- `correlationId`: UUID per HTTP request (from `X-Correlation-ID` header or auto-generated) or per Kafka event
- `topic`: Kafka topic name (on consumer log lines)
- `eventId`: External ID of the event being processed

Log levels by layer:
- **Kafka consumers**: `INFO` for event processing, `WARN` for skipped events, `ERROR` for failures
- **Service layer**: `DEBUG` for all operations (upserts, lookups, listings), `WARN` for not-found resources
- **Exception handler**: `ERROR` for unhandled exceptions

To enable service-level debug logging in production:
```yaml
logging.level.com.catalog.service: DEBUG
```

## Testing

91 tests across 4 categories:

| Category | Tests | Description |
|----------|-------|-------------|
| **Unit** | 47 | SemVer parsing, sort key encoding, mappers, exception handler (all HTTP status codes), Kafka consumers (mocked with JSON payloads), correlation filter, health indicator (mocked), entity lifecycle |
| **Controller Slice** | 6 | `@WebMvcTest` with mocked services for all REST endpoints |
| **Integration** | 9 | Real PostgreSQL: upserts, idempotency, ghost record pattern, SemVer sorting |
| **End-to-End** | 5 | `@EmbeddedKafka`: full plain-JSON Kafka event -> DB -> REST API flow, ghost promotion, SemVer sort, idempotency, correlation ID propagation |

Integration tests require PostgreSQL via `docker-compose up -d postgres`. E2E tests use an in-process embedded Kafka broker (no Docker needed for Kafka).

## Assumptions

- Kafka messages are expected to be valid JSON matching the documented schema. Malformed messages are caught, logged at ERROR level, and counted via the `catalog.events.processed{status=failure}` metric. The consumer continues processing subsequent messages without blocking.
- The `version` field follows SemVer 2.0.0 format: `MAJOR.MINOR.PATCH` with optional pre-release label (`-alpha.1`) and build metadata (`+build.123`). An optional `v` prefix is accepted. Leading zeros in numeric components are rejected.
- Ghost application records (with null `name`) are visible in the list applications endpoint. This is an intentional design choice: they exist because releases reference them.
- Pagination size is capped at 100 to prevent unbounded queries.

## Improvements Given More Time

- **Dead Letter Topic**: Route failed Kafka messages to a DLT for investigation instead of infinite retries.
- **Pagination Links**: Add HATEOAS-style navigation links to paginated responses.
- **API Versioning Strategy**: Content negotiation or header-based versioning as the API evolves.
- **Security**: API authentication/authorization (e.g., OAuth2 resource server).
- **Caching**: Spring Cache on read endpoints with Kafka event-driven invalidation.
- **Database connection pooling tuning**: Optimize HikariCP settings based on load testing.
