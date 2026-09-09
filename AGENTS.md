# AGENTS.md

This file provides guidance to Codex and other coding agents when working with code in this repository.

## Project Overview

Vigilo Framework (VF) is a multi-tenant Java framework for building professional event-driven monitoring systems. It provides the infrastructure for configuring, deploying, assigning, and executing lambdas; managing their inputs and state; and integrating devices and external services. Lambdas are independently developed Python plugins that interpret events delivered by the server and decide how to respond. External developers can add or modify this logic without rebuilding VF; the plugin implementations are not part of this repository. With domain-specific lambdas installed, the framework can support caregiver and medical monitoring workflows, but it does not itself implement patient-monitoring logic.

## Build Commands

```bash
# Full build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build specific module
mvn -pl core clean install

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RuleDaoTest

# Run tests in a specific module
mvn -pl core test

# Run a Spring Boot module (worker or api)
mvn -pl worker spring-boot:run
```

## Architecture

### Module Dependencies

```
foundation ← registry ← core ← [worker, api]
foundation ← { aws, messaging } ← core
foundation ← registry ← report ← api
foundation ← mcp
```

- **foundation**: Low-level shared utilities, logging, exception classes, properties, JSON mapping, and concurrency support. All other modules depend on this directly or transitively.
- **aws**: AWS SDK v2 integration (EC2, ECS, S3, SQS, SNS, Lambda, etc.). Depends on foundation.
- **messaging**: Message streaming layer — broker-agnostic producer/listener SPI (`dev.olegz.vf.messaging`) and the Kafka implementation (`dev.olegz.vf.messaging.kafka`); owns the `kafka-clients` dependency. Future brokers (RabbitMQ, Artemis) plug in as sibling implementation packages. Depends on foundation.
- **registry**: Organizations, locations, users, devices, their MyBatis CRUD layer, assignment services, user authentication, and authorization. Depends on foundation and has no dependency on aws or messaging.
- **core**: Lambda development, deployment, assignment, execution, state, scheduling, notification, and integration services. Depends on registry, aws, and messaging; worker and api depend on this.
- **report**: Standalone report persistence, generation, and REST service. Depends on foundation and registry; api embeds its report configuration without coupling report back to core.
- **worker**: Asynchronous data processing, event streaming, and cron task scheduling
- **api**: REST API endpoints (Spring MVC) — user accounts, lambda configuration, lambda deployment, developer teams
- **mcp**: Model Context Protocol server over the local PostgreSQL database. Depends on foundation only.

### Key Packages

- `dev.olegz.vf.registry.dao` - Organization, location, user, device, and signing-key persistence
- `dev.olegz.vf.registry.service` - Registry CRUD, assignment, authentication, and authorization services
- `dev.olegz.vf.registry.domain` - Organization, location, user, and device domain objects
- `dev.olegz.vf.core.dao` - Lambda-platform DAO interfaces with MyBatis mappers in `dao.mapper`
- `dev.olegz.vf.core.service` - Lambda-platform and integration services
- `dev.olegz.vf.core.domain` - Lambda configuration, assignment, execution, and system domain objects
- `dev.olegz.vf.integration` - External service integrations (AWS, Zendesk, partners, other clouds)
- `dev.olegz.vf.api.action` - REST API action handlers
- `dev.olegz.vf.common` - foundational utilities (properties, exceptions, date/time, HTTP, local process execution)
- `dev.olegz.vf.common.props` - property loading, typed hot-path properties, and Logback property definers
- `dev.olegz.vf.common.io` - process execution and binary data buffers
- `dev.olegz.vf.common.objectmap` - Jackson-based byte and string object mappers
- `dev.olegz.vf.core.domain.lambdarun.input` - serialized lambda input and trigger-data contracts

### Data Access Pattern

Uses MyBatis (not JPA). DAOs are interfaces with corresponding XML mapper files.

### Work Persistence

Always prefer using Kafka instead of the database to persist work data when Kafka can satisfy the required durability, ordering, recovery, and operational guarantees.

Treat database-backed work queues, outboxes, retry schedules, claims, or equivalent persistence as a fallback that needs a concrete justification. Use them when Kafka cannot safely or practically meet a required invariant, such as atomic coupling with database state, queryable scheduling, lease or fencing semantics, or recovery of data that is not otherwise durably represented.

When proposing an architecture, evaluate and explain the Kafka-first option before introducing new database persistence for work data.

### Testing

Tests use JUnit 5 with Spring Test support.
DAO tests require database connection and use `@Transactional` with `@Rollback` for isolation.
Follow the `/generate-tests` skill guidelines when writing tests.

### Configuration

- MyBatis mapper resources in `registry/src/main/resources/vf/` and `core/src/main/resources/vf/`
- Properties files in `foundation/config/properties/`, `src/main/resources/vf/properties/`
- `common.props.PropertyStore` loads properties from property files (`%HOME_DIR%/config/properties/` and module resources), watched for changes
- worker and api modules use Spring Boot with `application.properties`
- When an API contract changes, always update `api/doc/openapi.yaml` and `postman/VF.postman_collection.json` in the same change.

## Technology Stack

- Java 25, Spring Boot 4.0, Spring Framework 7.0
- MyBatis 3.5 with Spring integration for database access
- PostgreSQL database
- Kafka 4.3 for general event streaming (in the `messaging` module)
- Local in-memory cache by CaffeineCache and Spring Framework cache annotations with cross-process invalidation
- Jackson 3 for JSON mapping using `dev.olegz.vf.common.objectmap.BytesMapper` and `StringMapper` wrapper classes; Jackson 2 annotations remain by Jackson 3 design
- SLF4J logging - follow rules/java/logging.md
- AWS SDK v2 (EC2, ECS, S3, SQS, SNS, Lambda, etc.)
- AWS SQS, Rabbit MQ, Azure Service Bus, Azure Event Hub for integration with partner systems

## Java Stream API

Prefer `dev.olegz.vf.common.util.CollectionOps` to the Java Stream API for a single collection
operation when the input collection may be `null`. Use the Stream API when chaining multiple
operations makes the processing pipeline clearer.
