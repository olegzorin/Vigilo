# Vigilo Framework

Vigilo Framework (VF) is a multi-tenant Java framework for building professional event-driven monitoring systems.
A Lambda is a Python plugin that interprets events delivered by VF and decides how to respond.
Lambdas allow external developers to add or modify event-processing logic without rebuilding the
VF framework. Each Lambda is built, versioned, and deployed independently to provide domain-specific monitoring behavior,
including caregiver and medical workflows.

## Modules

| Module     | Description                                                                                                                                |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| foundation | Common library shared by all modules: utilities, logging, properties, JSON mapping, exceptions                                             |
| aws        | AWS SDK v2 access layer: client factories, credentials/region/retry config, and service gateways (S3, CloudWatch Logs, SQS, IAM, ECR, IoT) |
| messaging  | Broker-agnostic message streaming layer (producer/listener SPI) with a Kafka implementation                                                |
| registry   | Organizations, locations, users, devices, their CRUD operations, and user authorization; independent of AWS and messaging                |
| core       | Lambda development, deployment, assignment, execution, state, scheduling, and integration services; depends on registry                     |
| worker     | Asynchronous data streaming and processing, including cron-expression task scheduling                                                      |
| api        | REST API: user accounts, lambda configuration, lambda deployment, and developer teams                                                            |
| mcp        | Model Context Protocol server providing read/write access to the local PostgreSQL database                                                 |

## Documentation

- [Concepts](docs/concepts.md) — project goals, domain language, reliability, and security model.
- [Architecture and processes](docs/architecture-and-processes.md) — module boundaries, runtime topology, and end-to-end flows.
- [Local installation and deployment](docs/local-installation.md) — configure PostgreSQL, Kafka, local AWS substitutes, reports, API, and worker.
