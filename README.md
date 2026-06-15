# Energy Control API

Spring Boot application for smart home energy control — Thermia heat pump management via Modbus TCP, Nordpool electricity price integration, and Sungrow inverter control.

## Features

- **Thermia heat pump control** — temperatures, compressor data, operation mode, heating/tap water/cooling enable states, heat curve and setpoints via Modbus TCP
- **Nordpool electricity prices** — scheduled fetch, storage, hourly averages, current price endpoint
- **Sungrow inverter control** — power limit management and feed-in threshold based on electricity price
- **Scheduled data collection** — heat pump readings stored to PostgreSQL every minute
- **REST API** — secured with HTTP Basic Auth, role-based access (READONLY / USER / ADMIN)
- **OpenAPI/Swagger UI** — available at `/swagger-ui.html`
- **Docker support** — multi-stage Dockerfile, ARM v7 image built via GitHub Actions

## Technology Stack

- **Java 17**, **Spring Boot 3.2**
- **PostgreSQL 17**
- **Gradle 8.5**
- **Flyway** — database migrations
- **Spring Security** — HTTP Basic Auth with BCrypt
- **j2mod** — Modbus TCP client

## Project Status

### ✅ Completed

- [x] Gradle project structure with all dependencies
- [x] Docker Compose configuration for PostgreSQL
- [x] Database schema with Flyway migrations
- [x] Domain models (HeatPumpReading with embedded objects)
- [x] Spring Data JPA repositories
- [x] Thermia Modbus TCP client
- [x] Configuration service (database-driven)
- [x] Data collection service
- [x] Scheduled data collection (every 60 seconds)
- [x] Spring Security (HTTP Basic Auth, BCrypt, method-level annotations)
- [x] Default admin user (username: admin, password: admin)
- [x] DTOs and MapStruct mappers
- [x] REST API controllers (Thermia, Nordpool, Sungrow, Heating)
- [x] Nordpool price scheduler and hourly average prices
- [x] Sungrow feed-in threshold automation

### 🚧 Pending

- [ ] OAuth2 Authorization Server — for smart home integrations (dependency included, not yet configured)
- [ ] Admin REST API for user management (CRUD via API, not only via DB)
- [ ] Admin REST API for OAuth2 client management
- [ ] Global exception handler (`@ControllerAdvice` with consistent error response format)
- [ ] Unit and integration tests (Testcontainers setup exists as dependency)

## Quick Start

### Prerequisites

- Java 17+
- Docker and Docker Compose

### Step 1: Start PostgreSQL

```bash
docker-compose -f docker/docker-compose.yml up -d
```

Database credentials:
- Database: `energy_control`
- Username: `energy_user`
- Password: `energy_pass`

### Step 2: Run the Application

```bash
gradlew.bat bootRun         # Windows
./gradlew bootRun           # Linux/Mac
```

API available at `http://localhost:8080`, Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Step 3: Default Admin Credentials

⚠️ **Change this password before exposing to any network.**

```
Username: admin
Password: admin
```

## Deployment

The app is packaged as a Docker image. GitHub Actions builds and pushes to `ghcr.io/iiilja/energy-control-api:latest` on every push to `main`.

To run on a server (first time):

```bash
docker run -d \
  --name energy-control-api \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=energy_control \
  -e DB_USERNAME=energy_user \
  -e DB_PASSWORD=energy_pass \
  -e SERVER_PORT=8080 \
  -e JAVA_OPTS="-Xms128m -Xmx384m" \
  -p 8080:8080 \
  --restart unless-stopped \
  ghcr.io/iiilja/energy-control-api:latest
```

To deploy an update:

```bash
docker pull ghcr.io/iiilja/energy-control-api:latest
docker restart energy-control-api
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `energy_control` | Database name |
| `DB_USERNAME` | `energy_user` | Database username |
| `DB_PASSWORD` | `energy_pass` | Database password |
| `SERVER_PORT` | `8080` | Application port |
| `JAVA_OPTS` | `-Xms256m -Xmx512m` | JVM options |
| `collection.interval.ms` | `60000` | Data collection interval |

### Runtime Configuration (database-driven)

Stored in the `system_configuration` table:

| Key | Description |
|-----|-------------|
| `collection.enabled` | Enable/disable heat pump data collection (`true`/`false`) |
| `thermia.host` | Thermia heat pump IP address |
| `sungrow.host` | Sungrow inverter IP address |

To disable data collection:
```sql
UPDATE system_configuration SET config_value = 'false' WHERE config_key = 'collection.enabled';
```

## Security

### Authentication

HTTP Basic Auth with BCrypt (strength 12).

### Roles

| Role | Access |
|------|--------|
| `ROLE_READONLY` | Read-only — view data |
| `ROLE_USER` | Read + write heat pump controls |
| `ROLE_ADMIN` | Full access including actuator, config, user management |
| `ROLE_API` | For OAuth2 clients and third-party integrations |

### Endpoints

| Pattern | Access |
|---------|--------|
| `/actuator/health` | Public |
| `/actuator/**` | ROLE_ADMIN |
| All others | Any authenticated user (role-dependent per endpoint) |

## API Overview

All endpoints under `/api/v1/`. Full docs at `/swagger-ui.html`.

| Prefix | Description |
|--------|-------------|
| `/api/v1/thermia` | Thermia heat pump — status, temperatures, control |
| `/api/v1/nordpool` | Nordpool electricity prices |
| `/api/v1/sungrow` | Sungrow inverter |
| `/api/v1/heating` | Heating setpoint schedules |
| `/actuator/health` | Health check (public) |

## Database Schema

| Table | Description |
|-------|-------------|
| `heat_pump_readings` | Thermia readings (temperatures, compressor, status, heat curve) |
| `nordpool_prices` | Nordpool hourly electricity prices |
| `system_configuration` | Runtime configuration key-value store |
| `users` | User accounts |
| `user_roles` | Role assignments |
| `oauth2_registered_clients` | OAuth2 client registrations |
| `oauth2_authorization` | OAuth2 tokens and grants |
| `oauth2_authorization_consent` | OAuth2 consent records |

## Project Structure

```
src/main/java/com/ilja/smarthome/energycontrol/
├── EnergyControlApiApplication.java
├── config/                        # Security, OpenAPI, Thermia config
├── domain/model/                  # JPA entities
├── repository/                    # Spring Data JPA repositories
├── service/                       # DataCollectionService, ConfigurationService, NordpoolPriceService
├── scheduler/                     # DataCollectionScheduler, NordpoolScheduler
├── thermia/                       # Thermia heat pump (client, service, controller, DTOs)
├── sungrow/                       # Sungrow inverter (client, service, controller)
├── nordpool/                      # Nordpool prices (service, controller)
├── heating/                       # Heating schedules (service, controller)
└── exception/                     # Custom exceptions
docker/
├── Dockerfile                     # Standard image
├── Dockerfile.armv7               # ARMv7 image (Banana Pi, etc.)
├── docker-compose.yml
└── postgres/init.sql
.github/workflows/
└── build-armv7.yml                # Builds and pushes ARMv7 image to ghcr.io
```

## Troubleshooting

### Thermia connection fails

```
ThermiaCommException: Cannot connect to Thermia heat pump
```

1. Verify the heat pump is reachable: `ping <thermia_ip>`
2. Test Modbus: `modpoll -t 3 -r 1 -c 1 -1 <thermia_ip>`
3. Check `thermia.host` in `system_configuration` table

### Data not collecting

1. Check logs: `docker logs -f energy-control-api`
2. Verify `collection.enabled = true` in `system_configuration`
3. Health check: `curl http://localhost:8080/actuator/health`

### Database connection issues

1. Verify PostgreSQL is running
2. Check `listen_addresses` in `postgresql.conf` includes the host Docker connects from
3. Verify the Docker subnet is allowed in `pg_hba.conf`

---

**Version**: 1.0.0-SNAPSHOT | **Updated**: 2026-06-15