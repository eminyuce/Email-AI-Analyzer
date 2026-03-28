# Email AI Analyzer

Spring Boot web application that reads emails, runs AI-assisted analysis (via Ollama), and displays prioritized results with a settings-driven workflow.

## Tech Stack

- Java 21
- Spring Boot 3.3.5
- Spring MVC + Thymeleaf
- Spring Data JPA + MySQL
- Spring Security
- Spring AI (Ollama)
- Actuator + Prometheus metrics

## Features

- Email analysis and scoring UI
- Settings profiles with Active/Inactive status
- Create **new** settings profile or **duplicate** from active profile
- SMTP connection test from Settings page
- AI endpoint test from Settings page
- Scheduler controls (start/stop + cron/date range/max emails)
- Job progress page + API
- Actuator endpoints with security

## Prerequisites

- JDK 21
- Maven 3.9+
- MySQL 8+
- Ollama instance reachable from the app

## Configuration

The app reads configuration from profile-based files under `src/main/resources`:

- `application.yml` -> shared defaults
- `application-local.yml` -> local defaults (default active profile)
- `application-dev.yml` -> development defaults
- `application-prod.yml` -> production defaults

Active profile can be selected with `SPRING_PROFILES_ACTIVE` (`local`, `dev`, or `prod`).

A full list of environment variables (including optional tuning) is in [`.env.example`](.env.example).

### Required / Common environment variables

Use these in IntelliJ Run Configuration (Environment variables):

```env
DB_URL=jdbc:mysql://localhost:3306/email_db
DB_USER=email_user
DB_PASSWORD=password
GROQ_API_KEY=

SECURITY_USER=admin
SECURITY_PASS=changeme
SECURITY_LOG_CREDENTIALS_ON_STARTUP=false
DEBUG_LOG_SECRETS=false
```

Ollama base URL is configured per settings profile in the app (not via `SPRING_AI_OLLAMA_BASE_URL`).

### Optional environment variables

```env
AI_TEST_CONNECT_TIMEOUT_SECONDS=10
AI_TEST_REQUEST_TIMEOUT_SECONDS=75
EMAIL_IMAP_CONNECTION_TIMEOUT_MS=10000
EMAIL_IMAP_TIMEOUT_MS=20000
EMAIL_IMAP_WRITE_TIMEOUT_MS=20000
EMAIL_IMAP_SSL_TRUST=*
```

GitHub Actions CI reads optional variables `GROQ_API_KEY` and `DEBUG_LOG_SECRETS` from repository **Variables**, and (for registry push) `DOCKER_USERNAME` / `DOCKER_PASSWORD` from **Encrypted Secrets**; see `.github/workflows/docker-image.yml`.

## Run Locally

```bash
mvn clean spring-boot:run
```

Run with a specific profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Application URL:

- <http://localhost:8080>

## Build

```bash
mvn clean package
```

## Authentication

- Login page: `/login`
- Default credentials come from:
  - `SECURITY_USER` (default: `admin`)
  - `SECURITY_PASS` (default: `changeme`)

## Main Routes

- `/` -> login redirect/home entry
- `/settings` -> active settings profile
- `/settings/list` -> all settings profiles
- `/emails` -> analyzed email list
- `/emails/{emailId}` -> email detail
- `/jobs/progress` -> UI progress page
- `/api/jobs/progress` -> progress API
- `/actuator/health` -> public health endpoint
- `/actuator/**` -> admin-protected actuator endpoints

## Settings Notes

- Settings are persisted in database table `app_settings` (JPA, `ddl-auto=update`).
- Only one profile should be Active at a time.
- After saving settings, refreshing the page should show persisted values.
- Database connection itself is application-level infrastructure and not managed from Settings UI.

## Development Notes

- Uses Lombok and MapStruct annotation processing.
- If building in IntelliJ, enable annotation processing:
  - `Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable`.

