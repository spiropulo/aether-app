## Aether – GraphQL + GCP Firestore + Pub/Sub (Gradle)

This is the **Aether** Spring Boot application. It uses **Google Cloud Firestore** for data storage and **Google Cloud Pub/Sub** for optional eventing, and exposes a **GraphQL** API (no REST) to its clients.

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

### Local development with Docker emulators

Use Docker for GCS and Pub/Sub emulators (Firestore can run via Firebase CLI or separately):

```bash
# Start GCS + Pub/Sub emulators
docker compose up -d

# Create Pub/Sub topics + estimate subscription (idempotent; requires gcloud CLI)
./scripts/init-pubsub.sh

# Run the app
./gradlew bootRun
```

| Service    | Port | Image                    |
|-----------|------|--------------------------|
| fake-gcs  | 9195 | fsouza/fake-gcs-server   |
| Pub/Sub   | 8085 | google-cloud-cli:emulators |
| mailpit   | 1025 (SMTP), 8025 (Web UI) | axllent/mailpit |
| twilio-mock | 4010 (HTTP mock) | stoplight/prism:5 |

The bucket (`aether-estimates`) is created automatically on app startup. **Mailpit** captures all outgoing emails for local SMTP testing; view them at http://localhost:8025. **twilio-mock** (Prism) mimics Twilio’s “create message” API so project SMS can be tested without real Twilio; see **SMS (local)** below. **fake-gcs-server** implements the GCS JSON API (required for the Java client). The Firebase Storage emulator uses a different protocol and is not compatible.

> **Firestore**: Run via `firebase emulators:start --only firestore` (default port 8075) or your Firebase config.

### PDF → Agent flow

When a PDF is uploaded:

1. The file is stored in GCS (or fake-gcs-server locally).
2. A record is saved in Firestore.
3. A message is published to the `estimate-events` Pub/Sub topic.
4. The **EstimatePubSubListener** (when configured) pulls messages, fetches the PDF from GCS, and POSTs it to the Project PDF Sync agent (Aether AI) at `aether.agent.project-pdf-sync-url`.

For the full flow locally: start the Aether AI agent on port 8055, create the topic and subscription (see Configuration), then run this app. The listener will forward each uploaded PDF to the agent.

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
  - `spring.cloud.gcp.pubsub.emulator-host` – from `PUBSUB_EMULATOR_HOST` or defaults to `127.0.0.1:8085` (IPv4; avoids Firebase/macOS `localhost` → `::1` issues)
- GCS emulator (local profile):
  - `aether.storage.emulator-host` – from `STORAGE_EMULATOR_HOST` or defaults to `http://localhost:9195`
  - Start the fake-gcs-server with: `docker compose up -d fake-gcs`
  - The bucket is created on app startup; no `gcloud` or real GCP credentials needed.
- Pub/Sub topics (local profile):
  - `aether.pubsub.tenant-topic` – from `AETHER_PUBSUB_TENANT_TOPIC` (default: `tenant-events`)
  - `aether.pubsub.estimate-topic` – from `AETHER_PUBSUB_ESTIMATE_TOPIC` (default: `estimate-events`)
  - `aether.pubsub.estimate-subscription` – from `AETHER_PUBSUB_ESTIMATE_SUBSCRIPTION` (default: `estimate-events-sub`). Listener pulls from this and forwards PDFs to the Project PDF Sync agent.
  - `aether.agent.project-pdf-sync-url` – from `AETHER_AGENT_PROJECT_PDF_SYNC_URL` (default in local profile: `http://localhost:8055/api/v1/project-pdf-sync/process`). Aether AI endpoint for PDF line-item import.

- Email (local profile):
  - `aether.mail.enabled` – from `AETHER_MAIL_ENABLED` (default: `true` for local). When false, `EmailService` is a no-op.
  - `spring.mail.host` – from `MAIL_HOST` (default: `localhost` for Mailpit).
  - `spring.mail.port` – from `MAIL_PORT` (default: `1025` for Mailpit).
  - `aether.mail.from` – from `AETHER_MAIL_FROM` (default: `dev@localhost`).
  - Start Mailpit: `docker compose up -d mailpit`. View captured emails at http://localhost:8025.
- **SMS (local — optional, Prism mock)**:
  - Start mock: `docker compose up -d twilio-mock` (OpenAPI spec: `docker/twilio-messages-mock.yaml`). Leave it running while testing SMS; if the app logs **connection refused** on port **4010**, the container is stopped or failed — run `docker compose ps` and `docker compose logs twilio-mock`.
  - Point the app at the mock and enable SMS, for example:
    ```bash
    export AETHER_SMS_ENABLED=true
    export TWILIO_API_BASE_URL=http://localhost:4010
    export TWILIO_ACCOUNT_SID=AC000000000000000000000000000000
    export TWILIO_AUTH_TOKEN=dev
    export TWILIO_FROM_NUMBER=+15555551234
    ```
  - **Production:** do not set `TWILIO_API_BASE_URL` (defaults to `https://api.twilio.com`) and use real Twilio credentials from the [Twilio Console](https://console.twilio.com/).
- Stripe (subscription payments):
  - `STRIPE_SECRET_KEY` – from [Stripe Dashboard](https://dashboard.stripe.com/test/apikeys). Use `sk_test_*` for dev.
  - `STRIPE_PUBLISHABLE_KEY` – use `pk_test_*` for dev.
  - **Dev / fake card**: In test mode, use card `4242 4242 4242 4242`, any future expiry, any CVC.
  - When Stripe is not configured, upgrades fall back to direct subscribe (no payment).
  - **Payment method expiration**: When the default payment method is within 15 days of expiring, Admins receive an email. Enable [Customer Portal](https://dashboard.stripe.com/settings/billing/portal) in Stripe for payment method updates.
- Email (production): Use a commercial SMTP provider (SendGrid, Mailgun, etc.). Set:
  - `AETHER_MAIL_ENABLED=true`
  - `MAIL_HOST` (e.g. `smtp.sendgrid.net` or `smtp.mailgun.org`)
  - `MAIL_PORT` (typically `587`)
  - `MAIL_USERNAME` and `MAIL_PASSWORD` (API key or SMTP credentials)
  - `AETHER_MAIL_FROM` (verified sender address)

  Prefer `./scripts/init-pubsub.sh` (idempotent; creates `tenant-events`, `estimate-events`, and `estimate-events-sub`; defaults to `PUBSUB_EMULATOR_HOST=127.0.0.1:8085`). Or run `gcloud` by hand with the same env and `--project` matching `GCP_PROJECT_ID` (default `aether`).

### Production deployment

Use the `prod` profile and configure via environment variables:

| Variable | Required | Description |
|----------|----------|-------------|
| `GCP_PROJECT_ID` | Yes | GCP project ID |
| `GCP_CREDENTIALS_LOCATION` | No* | Path to service account JSON (or use workload identity) |
| `GCS_BUCKET_NAME` | Yes | GCS bucket for PDF uploads |
| `GCS_ESTIMATE_FOLDER` | No | Folder prefix in bucket (default: `estimates`) |
| `AETHER_PUBSUB_ESTIMATE_TOPIC` | Yes | Pub/Sub topic for PDF processing |
| `AETHER_PUBSUB_ESTIMATE_SUBSCRIPTION` | Yes | Subscription for the listener that forwards PDFs to the agent |
| `AETHER_AGENT_PROJECT_PDF_SYNC_URL` | Yes† | Project PDF Sync agent (e.g. `https://agent.example.com/api/v1/project-pdf-sync/process`). Omit if Pub/Sub PDF forwarding is disabled. |
| `AETHER_PUBSUB_TENANT_TOPIC` | No | Pub/Sub topic for tenant events |
| `AETHER_SMS_ENABLED` | No | Set `true` to send project SMS via Twilio |
| `TWILIO_ACCOUNT_SID` | If SMS | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | If SMS | Twilio auth token |
| `TWILIO_FROM_NUMBER` | If SMS | Twilio sender number (E.164) |
| `TWILIO_API_BASE_URL` | No | Omit for production (defaults to `https://api.twilio.com`). Use `http://localhost:4010` only with local Prism mock. |
| `JWT_SECRET` | Yes | Min 32 chars for HS256 |
| `JWT_EXPIRATION_MS` | No | Token expiry in ms (default: 86400000) |

\* In Cloud Run, use workload identity; otherwise set `GOOGLE_APPLICATION_CREDENTIALS` or `GCP_CREDENTIALS_LOCATION`.

†Required when the estimate Pub/Sub listener is enabled and should call Aether AI.

Example: `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun` (with env vars set).

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

All operations require a `tenantId` argument to enforce per-tenant isolation at the application layer. Tenant lifecycle events can optionally be emitted to Pub/Sub if `aether.pubsub.tenant-topic` is configured.
