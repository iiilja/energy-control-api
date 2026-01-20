# Docker Guide - Energy Control API

Complete guide for running the Energy Control API with Docker.

## Quick Start

### 1. Database Only (Recommended for Development)

```bash
# Start PostgreSQL
docker-compose up -d

# Run Spring Boot application locally
gradlew.bat bootRun
```

This is the **recommended approach** during development:
- Database runs in Docker (consistent environment)
- Application runs locally (fast iteration, hot reload)

### 2. Full Stack in Docker

```bash
# Start database + application
docker-compose --profile app up -d

# View logs
docker-compose logs -f app
```

Use this for:
- Testing the complete containerized setup
- Production-like environment
- CI/CD pipelines

### 3. Everything (Database + App + pgAdmin)

```bash
# Start all services
docker-compose --profile app --profile admin up -d

# Access pgAdmin at http://localhost:5050
# Email: admin@energy-control.local
# Password: admin
```

## Docker Compose Profiles

The `docker-compose.yml` uses profiles to optionally start services:

| Profile | Services Started | Use Case |
|---------|-----------------|----------|
| (none) | postgres | Development (app runs locally) |
| `app` | postgres + app | Full containerized stack |
| `admin` | postgres + pgAdmin | Database administration |
| `app,admin` | postgres + app + pgAdmin | Everything |

## Services

### PostgreSQL (Always Runs)

**Container**: `energy-control-postgres`
**Port**: `5432`
**Credentials**:
- Database: `energy_control`
- Username: `energy_user`
- Password: `energy_pass`

**Volumes**:
- `postgres_data` - Database files (persistent)
- `./docker/postgres/init.sql` - Initialization script

**Health Check**: Checks `pg_isready` every 10 seconds

### Spring Boot Application (Profile: app)

**Container**: `energy-control-api`
**Port**: `8080`
**Build Context**: Root directory
**Dockerfile**: `docker/Dockerfile`

**Environment Variables**:
- `SPRING_PROFILES_ACTIVE` - Spring profile (default: dev)
- `DB_HOST` - Database host (postgres)
- `DB_PORT` - Database port (5432)
- `DB_NAME` - Database name
- `DB_USERNAME` - Database user
- `DB_PASSWORD` - Database password
- `SERVER_PORT` - Application port (8080)

**Volumes**:
- `./logs` - Application logs (host-mounted)

**Health Check**: Checks `/actuator/health` every 30 seconds

### pgAdmin (Profile: admin)

**Container**: `energy-control-pgadmin`
**Port**: `5050`
**Access**: http://localhost:5050

**Credentials**:
- Email: `admin@energy-control.local`
- Password: `admin`

**Volumes**:
- `pgadmin_data` - pgAdmin configuration (persistent)

## Common Commands

### Starting Services

```bash
# Database only
docker-compose up -d

# Database + app
docker-compose --profile app up -d

# Database + app + pgAdmin
docker-compose --profile app --profile admin up -d

# Start and follow logs
docker-compose --profile app up
```

### Stopping Services

```bash
# Stop all running services
docker-compose down

# Stop and remove volumes (⚠️ deletes data!)
docker-compose down -v

# Stop specific service
docker-compose stop app
docker-compose stop postgres
```

### Viewing Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f app
docker-compose logs -f postgres
docker-compose logs -f pgadmin

# Last 100 lines
docker-compose logs --tail=100 app

# Since timestamp
docker-compose logs --since 2025-12-24T10:00:00 app
```

### Building & Rebuilding

```bash
# Build application image
docker-compose build app

# Build without cache (clean build)
docker-compose build --no-cache app

# Rebuild and restart
docker-compose --profile app up -d --build
```

### Container Management

```bash
# List running containers
docker-compose ps

# List all containers (including stopped)
docker-compose ps -a

# Restart service
docker-compose restart app

# Remove stopped containers
docker-compose rm
```

### Accessing Containers

```bash
# Execute command in running container
docker-compose exec app sh
docker-compose exec postgres bash

# Run one-off command
docker-compose run --rm app java -version

# Access PostgreSQL CLI
docker-compose exec postgres psql -U energy_user -d energy_control
```

### Database Operations

```bash
# Backup database
docker-compose exec postgres pg_dump -U energy_user energy_control > backup.sql

# Restore database
docker-compose exec -T postgres psql -U energy_user -d energy_control < backup.sql

# View database size
docker-compose exec postgres psql -U energy_user -d energy_control -c "\l+"

# View tables
docker-compose exec postgres psql -U energy_user -d energy_control -c "\dt"

# Query data
docker-compose exec postgres psql -U energy_user -d energy_control -c "SELECT COUNT(*) FROM heat_pump_readings;"
```

### Monitoring

```bash
# Check container status
docker-compose ps

# View resource usage
docker stats energy-control-postgres energy-control-api

# View health status
docker inspect --format='{{.State.Health.Status}}' energy-control-api

# Inspect network
docker network inspect energy-control-api_energy-network
```

## Environment Variables

Create a `.env` file in the project root to customize settings:

```bash
# Copy example file
cp .env.example .env

# Edit as needed
nano .env
```

**Available Variables**:
```env
SPRING_PROFILE=dev
DB_HOST=localhost
DB_PORT=5432
DB_NAME=energy_control
DB_USERNAME=energy_user
DB_PASSWORD=energy_pass
SERVER_PORT=8080
COLLECTION_INTERVAL_MS=60000
```

## Volumes

### postgres_data

**Type**: Named volume
**Purpose**: Persistent database storage
**Location**: Managed by Docker

**Operations**:
```bash
# Inspect volume
docker volume inspect energy-control-api_postgres_data

# Backup volume
docker run --rm -v energy-control-api_postgres_data:/data -v $(pwd):/backup alpine tar czf /backup/postgres-backup.tar.gz /data

# Remove volume (⚠️ deletes all data!)
docker-compose down -v
```

### pgadmin_data

**Type**: Named volume
**Purpose**: pgAdmin configuration and saved connections
**Location**: Managed by Docker

### Logs (Host Mount)

**Type**: Bind mount
**Source**: `./logs`
**Target**: `/app/logs`
**Purpose**: Application logs accessible on host

## Networking

**Network Name**: `energy-network`
**Driver**: bridge
**Purpose**: Internal communication between services

**Service DNS Names**:
- `postgres` - PostgreSQL database
- `app` - Spring Boot application
- `pgadmin` - pgAdmin web interface

Services can communicate using these DNS names (e.g., `jdbc:postgresql://postgres:5432/energy_control`)

## Troubleshooting

### Application Won't Start

**Check database health:**
```bash
docker-compose ps
docker-compose logs postgres
```

**Verify database is ready:**
```bash
docker-compose exec postgres pg_isready -U energy_user
```

**Check application logs:**
```bash
docker-compose logs app
```

### Database Connection Errors

**Verify network:**
```bash
docker network ls
docker network inspect energy-control-api_energy-network
```

**Test connection from app container:**
```bash
docker-compose exec app sh
ping postgres
nc -zv postgres 5432
```

### Port Conflicts

If ports 5432, 8080, or 5050 are already in use:

**Option 1: Stop conflicting service**

**Option 2: Change ports in docker-compose.yml**
```yaml
services:
  postgres:
    ports:
      - "15432:5432"  # Use different host port
```

### Performance Issues

**Check resource usage:**
```bash
docker stats
```

**Increase memory limits:**
```yaml
services:
  app:
    deploy:
      resources:
        limits:
          memory: 1G
```

### Database Migration Failures

**View Flyway logs:**
```bash
docker-compose logs app | grep Flyway
```

**Manually run migrations:**
```bash
docker-compose exec app java -Dspring.flyway.repair=true -jar app.jar
```

**Reset database (⚠️ deletes all data!):**
```bash
docker-compose down -v
docker-compose up -d
```

## Production Deployment

### 1. Use Production Profile

```bash
SPRING_PROFILE=prod docker-compose --profile app up -d
```

### 2. Secure Credentials

**Never use default passwords in production!**

Update `.env`:
```env
DB_PASSWORD=<strong-random-password>
ADMIN_PASSWORD=<strong-random-password>
```

### 3. Configure Resources

Add resource limits:
```yaml
services:
  app:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
        reservations:
          memory: 512M
```

### 4. Enable SSL/TLS

Use a reverse proxy (nginx, traefik) for HTTPS:
```yaml
services:
  nginx:
    image: nginx:alpine
    ports:
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./certs:/etc/nginx/certs
```

### 5. Set Up Monitoring

Add monitoring stack:
```yaml
  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
```

### 6. Backup Strategy

**Automated backups:**
```bash
# Add to crontab
0 2 * * * cd /path/to/project && docker-compose exec postgres pg_dump -U energy_user energy_control | gzip > backup-$(date +\%Y\%m\%d).sql.gz
```

## Best Practices

1. **Use profiles** - Don't run everything if you don't need it
2. **Mount logs** - Makes troubleshooting easier
3. **Use health checks** - Ensures services are actually ready
4. **Resource limits** - Prevents one service from consuming all resources
5. **Named volumes** - Better than anonymous volumes for data persistence
6. **Environment files** - Keep secrets out of docker-compose.yml
7. **Network isolation** - Each project should have its own network
8. **Regular backups** - Automate database backups
9. **Update images** - Regularly pull latest base images
10. **Monitor logs** - Set up log rotation and monitoring

## References

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [PostgreSQL Docker Image](https://hub.docker.com/_/postgres)
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)

---

**Last Updated**: 2025-12-24
**Docker Compose Version**: 3.8
**PostgreSQL Version**: 16-alpine
**Java Version**: 17
