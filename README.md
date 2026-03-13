## Aather – GraphQL + GCP Firestore + Pub/Sub (Gradle)

This is the **Aather** Spring Boot application. It uses **Google Cloud Firestore** for data storage and **Google Cloud Pub/Sub** for optional eventing, and exposes a **GraphQL** API (no REST) to its clients.

The initial GraphQL surface fully manages multi-tenant `Tenant` entities. Every tenant belongs to a `tenantId`, and all queries and mutations require a `tenantId` so that data is isolated per tenant.

### Tech stack

- **Build**: Gradle
- **Runtime**: Java 17
- **Framework**: Spring Boot 3.x
- **GCP**: Firestore (Native mode) & Pub/Sub via `spring-cloud-gcp` starters

### Gradle commands

From the project root:

- Build: `./gradlew build`
- Run: `./gradlew bootRun`

If you do not have the Gradle wrapper, you can generate it with a local Gradle install or run the tasks via `gradle` instead of `./gradlew`.

### Configuration & profiles

Base configuration is in `src/main/resources/application.yml`. Environment-specific overrides are in:

- `application-local.yml` – for local development against the GCP emulator running on your Mac.
- `application-prod.yml` – for running in GCP (e.g. Cloud Run).

Key properties:

- `spring.cloud.gcp.project-id` – taken from env `GCP_PROJECT_ID` (or set in the profile files).
- Firestore emulator (local profile):
  - `spring.cloud.gcp.firestore.emulator.enabled=true`
  - `spring.cloud.gcp.firestore.host-port` – from `FIRESTORE_EMULATOR_HOST` or defaults to `localhost:8075`
- Pub/Sub emulator (local profile):
  - `spring.cloud.gcp.pubsub.emulator-host` – from `PUBSUB_EMULATOR_HOST` or defaults to `localhost:8085`
- Optional Pub/Sub tenant events topic:
  - `aather.pubsub.tenant-topic` – from `AATHER_PUBSUB_TENANT_TOPIC` (e.g. `tenant-events`).

### GCP credentials

The application uses **Application Default Credentials**. Typical options:

- Set `GOOGLE_APPLICATION_CREDENTIALS` to point to a service account JSON key file:
  - `export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json`
- Or use `gcloud auth application-default login` during local development.

Make sure the service account has:

- Firestore permissions (e.g. `Cloud Datastore User`).
- Pub/Sub permissions (e.g. `Pub/Sub Publisher` for the topic).

### GraphQL API

The GraphQL endpoint is exposed at `/graphql`. Schema is defined in `src/main/resources/graphql/schema.graphqls`.

**Types**

- `Tenant` – core tenant object, including `id`, `tenantId`, `email`, `displayName`, `status`, `subscriptionPlan`, `createdAt`, and `updatedAt`.
- `TenantStatus` – `ACTIVE`, `INACTIVE`, `SUSPENDED`.

**Queries**

- `tenants(tenantId: String!): [Tenant!]!` – list all tenants for a tenant.
- `tenant(id: ID!, tenantId: String!): Tenant` – get a single tenant.

**Mutations**

- `createTenant(input: CreateTenantInput!): Tenant!`
- `updateTenant(id: ID!, tenantId: String!, input: UpdateTenantInput!): Tenant!`
- `deleteTenant(id: ID!, tenantId: String!): Boolean!`

All operations require a `tenantId` argument to enforce per-tenant isolation at the application layer. Tenant lifecycle events can optionally be emitted to Pub/Sub if `aather.pubsub.tenant-topic` is configured.
