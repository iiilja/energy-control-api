# Energy Control API

A Spring Boot application for collecting, storing, and exposing heat pump data from ESP32 Thermia devices via REST APIs.

## Features

- **Automated Data Collection**: Fetches heat pump data from ESP32 every minute
- **PostgreSQL Storage**: Stores all readings with full historical data
- **RESTful API**: Query historical data, statistics, and latest readings
- **OAuth2 Authorization**: Full OAuth2 provider for smart home integrations
- **Admin Interface**: Manage configuration, users, and OAuth2 clients
- **Docker Support**: PostgreSQL runs in Docker container
- **Comprehensive Monitoring**: Spring Boot Actuator with health checks and metrics

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **PostgreSQL 16** (Docker)
- **Gradle** (Build tool)
- **Flyway** (Database migrations)
- **Spring Security** (Authentication/Authorization)
- **OAuth2 Authorization Server**

## Project Status

### ✅ Completed (Core Functionality)
- [x] Gradle project structure with all dependencies
- [x] Docker Compose configuration for PostgreSQL
- [x] Database schema with Flyway migrations
- [x] Domain models (HeatPumpReading with embedded objects)
- [x] Spring Data JPA repositories
- [x] ESP32 HTTP client service
- [x] Configuration service (database-driven)
- [x] Data collection service (JSON parsing and mapping)
- [x] Scheduled data collection (every 60 seconds)
- [x] Exception handling
- [x] Logging configuration
- [x] Spring Security configuration (HTTP Basic Auth)
- [x] Default admin user (username: admin, password: admin)
- [x] BCrypt password hashing (strength 12)
- [x] Method-level security annotations

### 🚧 Pending (To be implemented)
- [ ] DTOs and MapStruct mappers
- [ ] REST API controllers (data access, admin, OAuth2)
- [ ] OAuth2 authorization server configuration
- [ ] Global exception handler
- [ ] Unit and integration tests

## Quick Start

> 📘 **For comprehensive Docker documentation**, see [DOCKER.md](DOCKER.md)

### Prerequisites

- **Java 17** or higher
- **Docker** and Docker Compose
- **Gradle** (or use included wrapper)
- **ESP32 device** with Thermia API running (see `thermia-heat-control` project)

### Step 1: Start PostgreSQL Database

```bash
cd energy-control-api
docker-compose up -d
```

This starts PostgreSQL 16 on port `5432`.

**Database Credentials:**
- Database: `energy_control`
- Username: `energy_user`
- Password: `energy_pass`

**Optional Services:**
```bash
# Start with pgAdmin (database management UI on port 5050)
docker-compose --profile admin up -d

# Start with the Spring Boot app (full stack)
docker-compose --profile app up -d

# Start everything (database + app + pgAdmin)
docker-compose --profile app --profile admin up -d
```

### Step 2: Run the Application

The ESP32 IP address is pre-configured to `http://192.168.8.125`. If your ESP32 is on a different IP, you can update it later via SQL or admin API (when implemented).

Using Gradle wrapper (Windows):
```bash
gradlew.bat bootRun
```

Using Gradle wrapper (Linux/Mac):
```bash
./gradlew bootRun
```

### Step 3: Default Admin Credentials

⚠️ **IMPORTANT**: A default admin user is created automatically:
- **Username**: `admin`
- **Password**: `admin`
- **Roles**: ROLE_ADMIN, ROLE_USER

**⚠️ CHANGE THIS PASSWORD IMMEDIATELY IN PRODUCTION!**

The application will:
1. Connect to PostgreSQL
2. Run Flyway migrations to create tables
3. Start data collection scheduler (fetches data every 60 seconds)
4. Expose REST APIs on `http://localhost:8080`

### Step 4: Test Authentication

Test that authentication is working:

```bash
# Health check (public - no auth required)
curl http://localhost:8080/actuator/health

# Metrics (requires admin credentials)
curl -u admin:admin http://localhost:8080/actuator/metrics
```

### Step 5: Verify Data Collection

Check the logs for successful data collection:
```
INFO - Starting scheduled data collection
INFO - Successfully collected and stored reading with ID: 1, Outdoor Temp: -5.2°C
```

Query the database to see stored readings:
```sql
SELECT id, collection_timestamp, outdoor_temp, compressor_running
FROM heat_pump_readings
ORDER BY collection_timestamp DESC
LIMIT 10;
```

## Configuration

### Application Properties

Key configuration properties in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `energy_control` | Database name |
| `DB_USERNAME` | `energy_user` | Database username |
| `DB_PASSWORD` | `energy_pass` | Database password |
| `collection.interval.ms` | `60000` | Data collection interval (milliseconds) |

### Database Configuration

Runtime configuration is stored in the `system_configuration` table:

| Key | Default Value | Description |
|-----|---------------|-------------|
| `esp32.base_url` | `http://192.168.8.125` | ESP32 device URL |
| `collection.interval_seconds` | `60` | Collection interval |
| `collection.enabled` | `true` | Enable/disable collection |
| `collection.timeout_seconds` | `10` | HTTP timeout |

**To disable data collection:**
```sql
UPDATE system_configuration
SET config_value = 'false'
WHERE config_key = 'collection.enabled';
```

## Database Schema

### Main Tables

1. **heat_pump_readings** - All ESP32 data readings
   - Status (connected, operation mode, alarms, etc.)
   - Temperatures (9 temperature sensors)
   - Compressor data (RPM, speed, hours)
   - Heating data (setpoint, hours)
   - Heat curve (arrays of temperature points)
   - Pump control (auto mode, state, timings)
   - Raw JSON (full ESP32 response)

2. **system_configuration** - Runtime configuration key-value store

3. **users** - User authentication

4. **user_roles** - User authorization (ROLE_READONLY, ROLE_USER, ROLE_ADMIN, ROLE_API)

5. **oauth2_registered_clients** - OAuth2 client registration

6. **oauth2_authorization** - OAuth2 tokens and grants

7. **oauth2_authorization_consent** - OAuth2 consent tracking

## Security & Authentication

### Authentication Methods

**Current**: HTTP Basic Authentication (username/password)
**Future**: OAuth2 Authorization Server (Phase 8)

### Security Rules

| Endpoint Pattern | Access Level | Roles Required |
|-----------------|--------------|----------------|
| `/actuator/health` | Public | None |
| `/actuator/**` | Protected | ROLE_ADMIN |
| All other endpoints | Protected | Any authenticated user |

### User Roles

- **ROLE_READONLY** - Read-only access (view heat pump data, no modifications)
- **ROLE_USER** - Standard user access (read/write heat pump data)
- **ROLE_ADMIN** - Administrative access (actuator endpoints, configuration management, user management)
- **ROLE_API** - API client access (for OAuth2 clients and third-party integrations)

### Password Security

- **Algorithm**: BCrypt
- **Strength**: 12 rounds (2^12 iterations)
- **Storage**: Password hashes stored in `users.password_hash` column
- **Never** store passwords in plain text

### Default Credentials

⚠️ **WARNING**: The default admin user has weak credentials:
```
Username: admin
Password: admin
```

**Change this immediately by**:
1. Creating a new admin user with strong password (future: via admin API)
2. Deleting or disabling the default admin user
3. Use a password manager to generate strong passwords (20+ characters)

## Development

### Project Structure

```
energy-control-api/
├── src/main/java/com/ilja/smarthome/energycontrol/
│   ├── EnergyControlApiApplication.java    # Main application
│   ├── config/                              # Configuration classes
│   │   └── RestClientConfig.java           # HTTP client config
│   ├── domain/model/                        # JPA entities
│   │   ├── HeatPumpReading.java            # Main entity
│   │   ├── StatusData.java                 # Embeddable
│   │   ├── TemperatureData.java            # Embeddable
│   │   ├── CompressorData.java             # Embeddable
│   │   ├── HeatingData.java                # Embeddable
│   │   ├── HeatCurveData.java              # Embeddable
│   │   ├── PumpData.java                   # Embeddable
│   │   ├── SystemConfiguration.java
│   │   ├── User.java
│   │   └── UserRole.java
│   ├── repository/                          # Spring Data JPA
│   │   ├── HeatPumpReadingRepository.java
│   │   ├── SystemConfigurationRepository.java
│   │   └── UserRepository.java
│   ├── service/                             # Business logic
│   │   ├── ConfigurationService.java       # Config management
│   │   ├── ESP32ClientService.java         # HTTP client
│   │   └── DataCollectionService.java      # Data collection
│   ├── scheduler/                           # Scheduled tasks
│   │   └── DataCollectionScheduler.java    # 1-minute collection
│   └── exception/                           # Custom exceptions
│       ├── ESP32CommunicationException.java
│       ├── ConfigurationNotFoundException.java
│       └── ResourceNotFoundException.java
└── src/main/resources/
    ├── application.yml                      # Main config
    ├── application-dev.yml                  # Dev profile
    ├── application-prod.yml                 # Prod profile
    └── db/migration/
        └── V1__initial_schema.sql          # Database schema
```

### Build Commands

```bash
# Build the project
gradlew build

# Run tests
gradlew test

# Run application
gradlew bootRun

# Build Docker image
docker-compose build app

# Clean build artifacts
gradlew clean
```

### Running with Docker

```bash
# Start database only (default)
docker-compose up -d

# Start database + application
docker-compose --profile app up -d

# Start database + application + pgAdmin
docker-compose --profile app --profile admin up -d

# View logs
docker-compose logs -f              # All services
docker-compose logs -f app          # Application only
docker-compose logs -f postgres     # Database only

# Stop all services
docker-compose down

# Stop and remove volumes (⚠️ deletes all data!)
docker-compose down -v

# Rebuild and restart
docker-compose --profile app up -d --build
```

**Useful Docker Commands:**
```bash
# Check running containers
docker-compose ps

# Execute SQL in database
docker-compose exec postgres psql -U energy_user -d energy_control

# Access application shell
docker-compose exec app sh

# View resource usage
docker stats energy-control-postgres energy-control-api
```

## Monitoring

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed health (requires admin)
curl -u admin:admin http://localhost:8080/actuator/health
```

### Metrics

```bash
# All metrics
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

### Logs

Logs are written to:
- **Console**: Standard output
- **File**: `logs/energy-control-api.log` (30 days retention, 10MB max size)

## Troubleshooting

### ESP32 Connection Issues

**Problem**: `ESP32CommunicationException: Cannot connect to ESP32`

**Solutions**:
1. Verify ESP32 is powered on and connected to network
2. Check ESP32 IP address in configuration
3. Test connectivity: `curl http://YOUR_ESP32_IP/api/data`
4. Check firewall rules

### Database Connection Issues

**Problem**: `Cannot connect to database`

**Solutions**:
1. Verify Docker container is running: `docker ps`
2. Check database credentials in `application.yml`
3. Ensure PostgreSQL port 5432 is not in use

### Data Not Collecting

**Problem**: No new readings in database

**Solutions**:
1. Check logs for errors
2. Verify `collection.enabled = true` in system_configuration table
3. Manually trigger collection to test (when admin API implemented)
4. Check ESP32 API is returning valid JSON

## ESP32 API Reference

The application expects ESP32 to expose `/api/data` endpoint returning:

```json
{
  "status": {
    "connected": true,
    "lastUpdate": 123456789,
    "operationMode": 3,
    "operationModeText": "ON/Auto",
    "alarmActive": false,
    "compressorRunning": true,
    "currentDemand": 4,
    "currentDemandText": "Heating"
  },
  "temperatures": {
    "outdoor": -5.2,
    "brineIn": -3.5,
    "brineOut": -6.8,
    "systemSupplyIn": 42.3,
    "systemSupplyOut": 38.1,
    "systemSupplyLine": 40.5,
    "systemSupplySetpoint": 41.0,
    "tapWaterTop": 52.4,
    "tapWaterLower": 48.2
  },
  "compressor": {
    "rpm": 2450,
    "speed": 65,
    "hours": 12543
  },
  "heating": {
    "setpoint": 21.5,
    "hours": 8932,
    "externalHeaterHours": 245
  },
  "heatCurve": {
    "outdoorTemp": [15.0, 10.0, 5.0, 0.0, -5.0, -10.0, -15.0],
    "supplyTemp": [25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 55.0]
  },
  "pump": {
    "autoMode": true,
    "currentState": false,
    "manualState": false,
    "onDuration": 1,
    "offDuration": 10,
    "lastStateChange": 123456789,
    "remainingMinutes": 8
  }
}
```

See `thermia-heat-control/thermia_modbus_esp32/API_README.md` for complete ESP32 API documentation.

## Next Steps

To complete the application, the following features need to be implemented:

1. **Security & Authentication**
   - Spring Security configuration with BCrypt password encoding
   - Default admin user creation
   - OAuth2 authorization server setup

2. **REST API Controllers**
   - HeatPumpDataController (GET /api/v1/heatpump/latest, /readings, /stats)
   - AdminConfigurationController (GET/PUT /api/v1/admin/config)
   - AdminUserController (CRUD operations for users)
   - AdminOAuth2Controller (OAuth2 client management)

3. **DTOs and Mappers**
   - Request/Response DTOs
   - MapStruct mappers for entity ↔ DTO conversion

4. **Error Handling**
   - Global exception handler with @ControllerAdvice
   - Consistent error response format

5. **Testing**
   - Unit tests for services
   - Integration tests with Testcontainers
   - OAuth2 flow tests

## License

This project is part of the ilja_smart_home repository.

## Support

For issues or questions:
1. Check the logs in `logs/energy-control-api.log`
2. Review ESP32 API connectivity
3. Verify database configuration
4. Check Docker container status

---

**Current Version**: 1.0.0-SNAPSHOT
**Last Updated**: 2025-12-24
