# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 3.2 / Java 17 / Gradle 8.5 / PostgreSQL app for home energy control: reads and controls a **Thermia heat pump** and a **Sungrow SG15RT inverter** over Modbus TCP (j2mod), and drives both from **Nordpool (Elering API, EE region) electricity prices**.

`AI_PROJECT_SUMMARY.md` is outdated: it describes an earlier ESP32/HTTP data source that has since been replaced by direct Modbus. Use `README.md` and the code instead.

## Commands

The Gradle wrapper (Gradle 8.5) is committed: use `gradlew.bat` on Windows and `./gradlew` on Linux/macOS. No global `gradle` install is needed.

```bash
docker-compose -f docker/docker-compose.yml up -d    # local PostgreSQL (energy_control / energy_user / energy_pass)
gradlew.bat bootRun                                   # run app (needs the wrapper jar); add --args='--spring.profiles.active=dev'
gradlew.bat build -x test                             # build jar (what the Dockerfiles do)
gradlew.bat test                                      # all tests
gradlew.bat test --tests 'com.ilja.smarthome.energycontrol.SomeTest'   # single test
```

Unit tests live in `src/test` (JUnit 5 + Mockito, no Spring context): `HeatingSetpointServiceTest` and `NordpoolPriceServiceTest` cover date/time logic (Tallinn wall-clock, DST days with 92/100 slots, stale prices). Services and schedulers that make time-based decisions take "now" from the `Clock` bean (`ClockConfig`). Don't call `ZonedDateTime.now()` directly in them. Exceptions are the entity audit timestamps (`@PrePersist`) and `DataCollectionService`'s collection timestamp. Day-range queries are half-open `[start, end)` (`findInPeriod`), because SQL `BETWEEN` would include the next day's 00:00 slot. Testcontainers dependencies are in place for repository tests.

Docker images: `docker/Dockerfile` (amd64), `docker/Dockerfile.arm64` (production target: H96 Pro+ box), `docker/Dockerfile.armv7` (legacy). GitHub Actions (`build-arm64.yml`) runs `./gradlew test` on every pull request and push to `main`. On a push to `main` with green tests it builds the arm64 image and pushes it to `ghcr.io/iiilja/energy-control-api:latest`, so merging to main deploys. The build compiles with a JDK 17 toolchain (`build.gradle`), and foojay downloads it if missing.

## Architecture

**Device integration packages** (`thermia/`, `sungrow/`) each contain `client` → `service` → `controller` plus `config/*Properties` and DTOs:
- `*ModbusClient` opens a **new TCP connection per operation** (no pooling) and wraps failures in `ThermiaCommException` / `SungrowCommunicationException`.
- **Register addressing:** Thermia and Sungrow docs use 1-based holding/input register numbers, and the clients subtract 1 internally, so pass the documented numbers. Thermia coils and discrete inputs are 0-based and are *not* offset. The Thermia protocol spec is `Thermia-Modbus-protocol_compressed.pdf` in the repo root (untracked).
- Device host, port and slave-id come from `application.yml` (`thermia.*`, `sungrow.*`, overridable with env vars such as `THERMIA_HOST`), not from the DB.

**Schedulers** (`scheduler/`) are the core automation. Each one checks a DB feature flag via `ConfigurationService.getConfigValue(key, default)` (the `system_configuration` key/value table), then catches and logs every exception so the schedule keeps running:
- `DataCollectionScheduler`: reads Thermia every `collection.interval.ms` and stores a `HeatPumpReading`. Flag: `collection.enabled`.
- `NordpoolPriceScheduler`: runs hourly 14:00–23:00 Europe/Tallinn and fetches today and tomorrow from Elering until tomorrow has every 15-minute slot (96, or 92/100 on DST days). Heating does not depend on prices. It also runs a daily cleanup. Flags: `nordpool.fetch.enabled`, `nordpool.cleanup.*`.
- `HeatingSetpointScheduler` runs `HeatingControlService.reconcile()` every 15 minutes, at :00:15, :15:15, :30:15 and :45:15. Flag: `heating.setpoint.adjustment.enabled`. Each run is a desired-state comparison, not a queue of commands:
  1. Read the setpoint on the heat pump.
  2. If it differs from what the pump reported after our last write (`heating_control_state`), someone changed it by hand. That value becomes a `heating_manual_override` (source `HEAT_PUMP`) for `heating.setpoint.manual_override.hours` (default 4).
  3. Desired setpoint, highest priority first: active manual override, then the price-slot override (`heating_setpoint_schedule`, one row per `NordpoolPrice`, written by price optimisation or `POST /heating/setpoint/schedule`), then the weekly template `default_weekly_schedule`, read directly via `HeatingSetpointService.scheduledSetpointAt`, then `heating.setpoint.default`.
  4. Write only if it differs. Before writing, the value is recorded as `pending_setpoint`. If the Modbus call fails after the pump applied it, the next run confirms it instead of taking it for a manual change. Override API calls first accept the current pump value as known, so a panel change the loop has not seen yet cannot take over the API request.
  The override API is `GET`/`POST`/`DELETE /api/v1/heating/setpoint/override` (source `API`, applied immediately). `POST /api/v1/thermia/setpoint` is also an override (`startOverride`), not a raw register write. With `heating.setpoint.adjustment.enabled=false` only the 15-minute loop stops: API overrides are still written to the pump, but they don't expire until the loop runs again.
- `SungrowFeedInScheduler`: runs every 15 minutes. If the current 15‑min price is below `feed-in-limit.price-threshold-eur-mwh`, it enables the inverter export limit at `feed-in-limit.limit-kw`; otherwise it disables the limit. Flag: `sungrow.feed_in_limit.enabled`.

Prices are 15‑minute granularity in the DB. `NordpoolPriceService` derives hourly averages on the fly. Business dates and times use `Europe/Tallinn`, while Hibernate JDBC uses UTC.

**Persistence:** JPA runs with `ddl-auto: validate`, so every schema change needs a Flyway migration in `src/main/resources/db/migration`. Newer migrations use date-based versions (`V20260430_1__...sql`). Follow that scheme, and seed new feature-flag keys into `system_configuration` from a migration.

**Security:** HTTP Basic with BCrypt, using users and roles from the DB (default `admin`/`admin` from V2). Access control is per endpoint via `@PreAuthorize` (`ROLE_READONLY` for reads, `ROLE_USER`/`ROLE_ADMIN` for writes, `ROLE_ADMIN` for admin operations), and `@EnableMethodSecurity` is on. `/actuator/health` and the Swagger paths are public. The OAuth2 authorization server dependency and tables exist but are not configured. `GlobalExceptionHandler` (`@RestControllerAdvice`) maps `InvalidValueException` (the services' "bad input" type) and Bean Validation `ConstraintViolationException` to 400. It deliberately leaves a plain `IllegalArgumentException` such as `NumberFormatException` alone, so those stay 500. It also maps Thermia/Sungrow communication errors to 503 and Elering errors to 502, as RFC 7807 problem details.

**Write guards:** `ThermiaHeatPumpService` range-checks every register write (comfort setpoint 15–30 °C, operation mode 1–3, tap water start 30–60 / stop 30–65 with start < stop, heat curve 7 points 15–65 °C) and throws `InvalidValueException` before touching Modbus. Keep the DTO constraints and these checks in sync, because schedulers bypass the DTOs.

Swagger UI: `http://localhost:8080/swagger-ui.html`. All API routes are under `/api/v1/{thermia,sungrow,nordpool,heating}`.
