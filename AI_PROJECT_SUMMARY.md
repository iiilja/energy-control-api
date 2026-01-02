# AI Project Summary - Energy Control API

**Last Updated**: 2025-12-24
**Status**: Core functionality + Security implemented, REST APIs pending
**Version**: 1.0.0-SNAPSHOT

## Project Purpose

Spring Boot 3.x application that:
1. **Collects** heat pump data from ESP32 device every 60 seconds via HTTP
2. **Stores** all readings in PostgreSQL database with full history
3. **Exposes** data via OAuth2-secured REST APIs for smart home integrations
4. **Manages** configuration through database (ESP32 IP, intervals, etc.)

## Quick Context

- **Repository**: `C:\Users\bkil\Documents\git\ilja_smart_home\energy-control-api\`
- **Related Project**: `thermia-heat-control\thermia_modbus_esp32\` (ESP32 firmware providing the data source)
- **Build Tool**: Gradle 8.5
- **Java Version**: 17
- **Database**: PostgreSQL 16 (runs in Docker)

## Technology Stack

```yaml
Core:
  - Spring Boot: 3.2.0
  - Java: 17
  - Database: PostgreSQL 16
  - Build: Gradle

Spring Modules:
  - spring-boot-starter-web (REST APIs)
  - spring-boot-starter-data-jpa (Database)
  - spring-boot-starter-security (Auth)
  - spring-boot-starter-oauth2-authorization-server (OAuth2 provider)
  - spring-boot-starter-validation (Input validation)
  - spring-boot-starter-actuator (Monitoring)

Database:
  - PostgreSQL driver
  - Flyway (migrations)
  - Hypersistence Utils (JSONB support)

Utilities:
  - Lombok (reduce boilerplate)
  - MapStruct (DTO mapping) - configured but not yet used
  - Jackson (JSON processing)

Testing:
  - spring-boot-starter-test
  - spring-security-test
  - Testcontainers (PostgreSQL integration tests)
```

## Architecture Overview

### Data Flow
```
ESP32 Device (Thermia Heat Pump)
    ↓ HTTP GET /api/data (every 60s)
ESP32ClientService
    ↓ JSON String
DataCollectionService (parses JSON)
    ↓ HeatPumpReading entity
HeatPumpReadingRepository
    ↓ Persist
PostgreSQL Database
```

### Package Structure
```
com.ilja.smarthome.energycontrol
├── EnergyControlApiApplication.java          # @SpringBootApplication + @EnableScheduling
├── config/                                    # Configuration classes
│   └── RestClientConfig.java                # HTTP client with 10s timeout
├── domain/model/                             # JPA entities
│   ├── HeatPumpReading.java                 # Main entity (id, timestamp, status, temps, etc.)
│   ├── StatusData.java                      # @Embeddable (operation mode, alarms, etc.)
│   ├── TemperatureData.java                 # @Embeddable (9 temperature sensors)
│   ├── CompressorData.java                  # @Embeddable (RPM, speed, hours)
│   ├── HeatingData.java                     # @Embeddable (setpoint, hours)
│   ├── HeatCurveData.java                   # @Embeddable (outdoor/supply temp arrays)
│   ├── PumpData.java                        # @Embeddable (auto mode, state, timings)
│   ├── SystemConfiguration.java             # Key-value config (id, key, value, description)
│   ├── User.java                            # implements UserDetails
│   └── UserRole.java                        # ManyToOne with User
├── repository/                               # Spring Data JPA
│   ├── HeatPumpReadingRepository.java       # findFirstByOrderByCollectionTimestampDesc()
│   ├── SystemConfigurationRepository.java   # findByConfigKey()
│   └── UserRepository.java                  # findByUsername()
├── service/                                  # Business logic
│   ├── ConfigurationService.java            # CRUD for system_configuration table
│   ├── ESP32ClientService.java              # RestClient wrapper, throws ESP32CommunicationException
│   └── DataCollectionService.java           # Orchestrates fetch + parse + save
├── scheduler/
│   └── DataCollectionScheduler.java         # @Scheduled(fixedDelay=60000) + manual trigger
└── exception/                                # Custom exceptions
    ├── ESP32CommunicationException.java
    ├── ConfigurationNotFoundException.java
    └── ResourceNotFoundException.java
```

## Database Schema

### Tables (7 total)

1. **heat_pump_readings** (Main data table)
   - Primary key: `id` (BIGSERIAL)
   - Unique: `collection_timestamp`
   - Fields: 40+ columns mapping ESP32 JSON structure
   - Indexes: timestamp DESC, date, operation_mode, outdoor_temp, JSONB, partial index (30 days)
   - Special: `raw_json` (JSONB) stores full ESP32 response

2. **system_configuration** (Runtime config)
   - Key-value pairs with description
   - Initial values: `esp32.base_url`, `collection.interval_seconds`, `collection.enabled`, `collection.timeout_seconds`
   - Automatically populated by V1 migration

3. **users** (Authentication)
   - username (unique), password_hash (BCrypt), email, enabled flags
   - Implements Spring Security UserDetails via User entity

4. **user_roles** (Authorization)
   - user_id FK to users, role (ROLE_READONLY, ROLE_USER, ROLE_ADMIN, ROLE_API)

5. **oauth2_registered_clients** (OAuth2 clients)
   - Spring Authorization Server standard schema

6. **oauth2_authorization** (OAuth2 tokens/grants)
   - Spring Authorization Server standard schema

7. **oauth2_authorization_consent** (OAuth2 consent)
   - Spring Authorization Server standard schema

### Flyway Migrations
- `V1__initial_schema.sql` - Creates all 7 tables + indexes + initial config data (✅ Complete)
- `V2__insert_default_admin.sql` - (✅ Complete) Creates default admin user (admin/admin)
- `V3__add_oauth2_tables.sql` - (Not needed - already in V1)

## ✅ What's Implemented (WORKING NOW)

### Phase 1: Foundation (✅ Complete - Dec 24, 2025)
- [x] Gradle build.gradle with all dependencies
- [x] settings.gradle, gradlew.bat, .gitignore
- [x] docker-compose.yml (PostgreSQL 16 + pgAdmin)
- [x] Dockerfile (multi-stage: Gradle build → JRE runtime)
- [x] application.yml (main config)
- [x] application-dev.yml (30s interval, debug logging)
- [x] application-prod.yml (production settings)

### Phase 2: Domain Layer (✅ Complete)
- [x] All 10 JPA entities with proper annotations
- [x] 6 @Embeddable classes for HeatPumpReading
- [x] User implements UserDetails for Spring Security
- [x] @PrePersist/@PreUpdate lifecycle hooks
- [x] All 3 Spring Data JPA repositories

### Phase 3: Core Services (✅ Complete - DATA COLLECTION WORKS!)
- [x] ConfigurationService - database-driven config management
- [x] ESP32ClientService - HTTP client with timeout and error handling
- [x] DataCollectionService - JSON parsing, mapping to entities
- [x] DataCollectionScheduler - @Scheduled every 60s, graceful error handling
- [x] Custom exceptions (ESP32Communication, ConfigurationNotFound, ResourceNotFound)
- [x] RestClientConfig - HTTP client bean with 10s timeout

### Phase 4: Security (✅ Complete - Dec 24, 2025)
- [x] SecurityConfig.java with BCrypt password encoder (strength 12)
- [x] UserDetailsService implementation (database-backed)
- [x] Default admin user migration (V2__insert_default_admin.sql)
- [x] HTTP Basic auth configuration
- [x] Method-level security (@EnableMethodSecurity)
- [x] Security rules (public health, protected actuator, authenticated APIs)

### Other (✅ Complete)
- [x] Comprehensive README.md with quick start guide and security documentation
- [x] Logging configuration (console + file rotation)
- [x] Docker support (PostgreSQL container)
- [x] Database schema with full ESP32 data model
- [x] ESP32 IP pre-configured to http://192.168.8.125

## ⏳ What's Pending (NOT IMPLEMENTED)

### Phase 5: REST API - Data Access
- [ ] DTOs (HeatPumpReadingResponse, TemperatureStatsResponse, etc.)
- [ ] MapStruct mappers (entity ↔ DTO)
- [ ] HeatPumpDataService (business logic for queries)
- [ ] HeatPumpDataController:
  - GET /api/v1/heatpump/latest
  - GET /api/v1/heatpump/readings?start=&end=&page=&size=
  - GET /api/v1/heatpump/stats/temperature?start=&end=
  - GET /api/v1/heatpump/stats/compressor?start=&end=

### Phase 6: REST API - Admin Configuration
- [ ] AdminConfigurationController:
  - GET /api/v1/admin/config (list all)
  - GET /api/v1/admin/config/{key}
  - PUT /api/v1/admin/config/{key}
  - POST /api/v1/admin/config/esp32/test-connection
  - POST /api/v1/admin/collection/trigger (manual collection)

### Phase 7: REST API - User Management
- [ ] UserService (CRUD operations)
- [ ] AdminUserController:
  - GET /api/v1/admin/users
  - POST /api/v1/admin/users
  - PUT /api/v1/admin/users/{id}
  - DELETE /api/v1/admin/users/{id}

### Phase 8: OAuth2 Authorization Server
- [ ] OAuth2AuthorizationServerConfig.java
- [ ] JWK source with RSA keys
- [ ] JWT decoder
- [ ] OAuth2ClientService
- [ ] AdminOAuth2Controller (client management)
- [ ] Update SecurityConfig for OAuth2 resource server

### Phase 9: Error Handling
- [ ] GlobalExceptionHandler (@ControllerAdvice)
- [ ] Consistent error response DTOs
- [ ] Bean validation on request DTOs

### Phase 10: Testing & Production
- [ ] Unit tests for services
- [ ] Integration tests with Testcontainers
- [ ] OAuth2 flow tests
- [ ] Health indicator for ESP32 connectivity
- [ ] Prometheus metrics configuration

## Key Design Decisions

1. **Embedded Objects vs Separate Tables**: Used @Embeddable (StatusData, TemperatureData, etc.) for tightly coupled data that's always queried together. Better performance, simpler queries.

2. **Raw JSON Storage**: Store full ESP32 JSON in JSONB column for:
   - Debugging
   - Flexibility if ESP32 API changes
   - Ad-hoc querying with PostgreSQL JSONB operators

3. **BigDecimal for Temperatures**: Precision is critical, avoid floating-point errors.

4. **Fixed Delay Scheduler**: Uses `fixedDelay` (not `fixedRate`) to prevent overlapping executions if ESP32 is slow/offline.

5. **Database-Driven Configuration**: ESP32 IP and runtime settings stored in `system_configuration` table (user requirement) rather than application.yml.

6. **Graceful Error Handling**: Scheduler continues on ESP32 errors (logs warning, doesn't crash app).

## Configuration Values

### Environment Variables
```yaml
DB_HOST: localhost (default)
DB_PORT: 5432
DB_NAME: energy_control
DB_USERNAME: energy_user
DB_PASSWORD: energy_pass
SERVER_PORT: 8080
COLLECTION_INTERVAL_MS: 60000 (1 minute)
```

### Database Configuration (system_configuration table)
```sql
esp32.base_url = 'http://192.168.8.125'  -- ✅ Pre-configured
collection.interval_seconds = '60'
collection.enabled = 'true'               -- Set to 'false' to disable
collection.timeout_seconds = '10'
```

## How to Run

### Start Database
```bash
cd C:\Users\bkil\Documents\git\ilja_smart_home\energy-control-api
docker-compose up -d
```

**Docker Compose Profiles:**
- (none) - PostgreSQL only (default, recommended for development)
- `--profile app` - PostgreSQL + Spring Boot application
- `--profile admin` - PostgreSQL + pgAdmin
- `--profile app --profile admin` - Everything

### ESP32 IP Configuration
ESP32 IP is pre-configured to `http://192.168.8.125` in the V1 migration.

If your ESP32 is on a different IP, update it:
```sql
UPDATE system_configuration
SET config_value = 'http://YOUR_ESP32_IP_HERE'
WHERE config_key = 'esp32.base_url';
```

### Run Application
```bash
cd C:\Users\bkil\Documents\git\ilja_smart_home\energy-control-api
gradlew.bat bootRun
```

### Test Authentication
```bash
# Public health check (no auth)
curl http://localhost:8080/actuator/health

# Protected endpoint (requires admin:admin)
curl -u admin:admin http://localhost:8080/actuator/metrics
```

Default credentials: **admin / admin** ⚠️ CHANGE IN PRODUCTION!

### Verify Data Collection
Check logs:
```
INFO - Starting scheduled data collection
INFO - Successfully collected and stored reading with ID: 1, Outdoor Temp: -5.2°C
```

Query database:
```sql
SELECT id, collection_timestamp, outdoor_temp, compressor_running
FROM heat_pump_readings
ORDER BY collection_timestamp DESC
LIMIT 10;
```

## ESP32 API Integration

### Source
- **Project**: `thermia-heat-control\thermia_modbus_esp32\`
- **Documentation**: `thermia-heat-control\thermia_modbus_esp32\API_README.md`
- **Endpoint**: `http://<ESP32_IP>/api/data`
- **Method**: GET
- **Response**: JSON (full spec in API_README.md)

### JSON Structure (Expected from ESP32)
```json
{
  "status": {
    "connected": boolean,
    "lastUpdate": long,
    "operationMode": int,
    "operationModeText": string,
    "alarmActive": boolean,
    "compressorRunning": boolean,
    "currentDemand": int,
    "currentDemandText": string
  },
  "temperatures": {
    "outdoor": float,
    "brineIn": float,
    "brineOut": float,
    "systemSupplyIn": float,
    "systemSupplyOut": float,
    "systemSupplyLine": float,
    "systemSupplySetpoint": float,
    "tapWaterTop": float,
    "tapWaterLower": float
  },
  "compressor": {
    "rpm": int,
    "speed": int,
    "hours": int
  },
  "heating": {
    "setpoint": float,
    "hours": int,
    "externalHeaterHours": int
  },
  "heatCurve": {
    "outdoorTemp": [float array - 7 elements],
    "supplyTemp": [float array - 7 elements]
  },
  "pump": {
    "autoMode": boolean,
    "currentState": boolean,
    "manualState": boolean,
    "onDuration": int,
    "offDuration": int,
    "lastStateChange": long,
    "remainingMinutes": int
  }
}
```

## File Locations (Critical Paths)

### Configuration
- `src/main/resources/application.yml` - Main config
- `src/main/resources/application-dev.yml` - Dev profile (30s interval)
- `src/main/resources/application-prod.yml` - Prod profile
- `build.gradle` - Dependencies and build config
- `docker-compose.yml` - Main Docker Compose (PostgreSQL + optional app/pgAdmin)
- `docker/Dockerfile` - Multi-stage Docker build
- `.env.example` - Environment variable template

### Database
- `src/main/resources/db/migration/V1__initial_schema.sql` - Full schema (270 lines)

### Core Business Logic
- `src/main/java/com/ilja/smarthome/energycontrol/service/DataCollectionService.java` - Main data collection orchestrator
- `src/main/java/com/ilja/smarthome/energycontrol/service/ESP32ClientService.java` - HTTP client
- `src/main/java/com/ilja/smarthome/energycontrol/scheduler/DataCollectionScheduler.java` - Scheduled task

### Domain Model
- `src/main/java/com/ilja/smarthome/energycontrol/domain/model/HeatPumpReading.java` - Main entity

### Documentation
- `README.md` - User-facing documentation (quick start, features, API)
- `DOCKER.md` - Comprehensive Docker guide (commands, troubleshooting, production)
- `AI_PROJECT_SUMMARY.md` - This file (AI context for future sessions)

## Common Queries for Future Sessions

### "Continue implementing the REST APIs"
→ Start with Phase 5: Create DTOs, MapStruct mappers, HeatPumpDataService, HeatPumpDataController

### "Add security/authentication"
→ Phase 4: Create SecurityConfig, default admin user migration, configure BCrypt

### "Add OAuth2 authorization server"
→ Phase 8: OAuth2AuthorizationServerConfig, client management

### "Why isn't data being collected?"
→ Check:
1. ESP32 IP in system_configuration table
2. collection.enabled = true
3. ESP32 device is online and accessible
4. Logs in `logs/energy-control-api.log`

### "How do I change collection interval?"
→ Two options:
1. Database: `UPDATE system_configuration SET config_value = '30' WHERE config_key = 'collection.interval_seconds'`
2. Application: Set `COLLECTION_INTERVAL_MS=30000` environment variable

### "Add a new REST endpoint"
→ Need to create:
1. DTO classes (request/response)
2. Service method (business logic)
3. Controller endpoint with @GetMapping/@PostMapping
4. Security annotations (@PreAuthorize)

## Current Limitations

1. **Basic Authentication Only**: HTTP Basic Auth implemented, but OAuth2 not yet available (Phase 8)
2. **Default Weak Credentials**: Default admin user has weak password (admin/admin) - change immediately!
3. **No REST APIs**: Can't query data via HTTP yet (only database queries)
4. **No Error UI**: Errors only visible in logs
5. **No Admin Interface**: Must use SQL to change configuration
6. **No Tests**: No unit/integration tests implemented yet

## Plan Reference

Full implementation plan available at:
- `C:\Users\bkil\.claude\plans\elegant-spinning-graham.md`

This plan has 10 phases, 50 steps. Currently completed: Phases 1-4 (foundation + core data collection + security).

## Success Metrics

**Current Status**: ✅ Core data collection + Security working
- PostgreSQL running in Docker: ✅
- Spring Boot starts successfully: ✅
- Database schema created: ✅
- Data collected every 60s: ✅
- Data stored in database: ✅
- Basic authentication works: ✅
- Default admin user created: ✅

**Future Success Criteria** (pending implementation):
- OAuth2 authorization server functional: ⏳
- REST API endpoints return data: ⏳
- Admin can manage config via API: ⏳
- Integration tests pass: ⏳

## Notes for AI

- **User chose**: Gradle (not Maven), Generic OAuth2 (not Home Assistant), Database config (not file-based)
- **ESP32 IP must be configured** before first run
- **Do not modify** the ESP32 firmware project (`thermia-heat-control/`) - it's a separate system
- **Security is intentionally delayed** - core functionality first, security later
- **MapStruct is configured** in build.gradle but not yet used (waiting for DTO creation in Phase 5)
- **OAuth2 tables already exist** in V1 migration (don't create V3 migration)
- **Testcontainers is configured** but no tests written yet

## Quick Reference: Main Classes

| Class | Purpose | Key Methods |
|-------|---------|-------------|
| `EnergyControlApiApplication` | Main entry point | `main()` |
| `DataCollectionScheduler` | Runs every 60s | `collectData()`, `triggerManualCollection()` |
| `DataCollectionService` | Orchestrates collection | `collectAndStoreData()`, `mapJsonToReading()` |
| `ESP32ClientService` | HTTP client | `fetchHeatPumpData()`, `testConnection()` |
| `ConfigurationService` | Config CRUD | `getConfigValue()`, `updateConfiguration()` |
| `HeatPumpReadingRepository` | Data access | `findFirstByOrderByCollectionTimestampDesc()` |

---

**Last Commit Context**: Completed Phase 4 (Security & Authentication) and reorganized Docker setup. Added ROLE_READONLY for read-only access, moved Dockerfile to docker/ directory, created comprehensive docker-compose.yml in root with profiles (app, admin), added DOCKER.md guide, and .env.example for environment variables. Docker setup now follows industry standards with optional services via profiles. Application has working authentication. Ready for Phase 5 (REST APIs).
