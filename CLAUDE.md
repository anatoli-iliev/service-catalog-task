# Software Catalog Service - Project Context & Rules

## Project Overview
This is a Spring Boot 4.x (Java 25) backend service that tracks software applications and their releases. It consumes asynchronous events from Kafka (`catalog.applications` and `catalog.releases`) and exposes a REST API (documented with OpenAPI 3).

## Tech Stack
* **Language/Framework:** Java 25, Spring Boot 4.x
* **Database:** PostgreSQL
* **Migrations:** Flyway
* **Messaging:** Kafka
* **Build Tool:** Gradle

## STRICT ARCHITECTURAL RULES (CRITICAL)

### 1. Database Schema & Identifiers
* **NEVER** use the external `applicationId` or `releaseId` (from Kafka payloads) as the internal Primary Key.
* Use auto-incrementing `BIGINT` or internal UUIDs for Primary Keys.
* Store external IDs in `external_application_id` and `external_release_id` columns with `UNIQUE` constraints.
* All Foreign Key relationships must use the internal Primary Keys.

### 2. Semantic Versioning Sorting
* **NEVER** store the release version as a single `VARCHAR` for sorting purposes. Standard string sorting will fail Semantic Version constraints.
* In the Flyway migration for the `releases` table, you MUST create integer columns for `version_major`, `version_minor`, and `version_patch`. 
* When parsing a release event, split the semantic version and populate these integer columns. API queries for releases MUST sort using `ORDER BY version_major DESC, version_minor DESC, version_patch DESC`.

### 3. Eventual Consistency & The Ghost Record Pattern
* Kafka topics are unordered. A release event may arrive BEFORE its corresponding application event.
* If a release event arrives with an unknown `applicationId`, do not throw an exception. Instead, immediately `UPSERT` a "stub" or "ghost" record into the applications table using just that external ID.
* When the application event eventually arrives, update that existing stub record with the actual metadata (name, description, etc.).

### 4. Idempotency & Duplicate Handling
* Assume at-least-once delivery from Kafka. Duplicate messages will happen.
* All database inserts must be idempotent. Use PostgreSQL `INSERT ... ON CONFLICT (external_id) DO UPDATE` to handle duplicates gracefully without throwing `DataIntegrityViolationException`.

## Build & Run Commands
* **Build:** `./gradlew clean build -x test`
* **Run Local Infrastructure:** `docker-compose up -d`
* **Run Application:** `./gradlew bootRun`
