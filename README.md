# EduTrack

EduTrack is a single-teacher learning management system. The repository contains a Java 21 / Spring Boot API, React / TypeScript frontend, Flyway migrations and a PostgreSQL Compose stack. See [PROJECT_STATUS.md](PROJECT_STATUS.md) for the current implementation and verification state; the product is still under development.

## Run locally

1. Copy `.env.example` to `.env`. Replace the database password, JWT secret and teacher initial password with private values. Keep `.env` out of Git.
2. Run `python3 scripts/check-env.py .env`.
3. Run `docker compose --profile dev up --build -d`. The frontend is at `http://localhost:5173`; Mailpit is at `http://localhost:8025`.
4. Check `docker compose ps` and `docker compose logs backend`. The backend readiness endpoint is `/actuator/health/readiness` on the internal container port. Flyway runs when the backend starts.

The frontend sends API requests to the same origin at `/api/v1`. Nginx proxies them to the backend, including refresh-token cookies. The administrator account is created from `ADMIN_EMAIL` and `ADMIN_INITIAL_PASSWORD` at startup. Do not use the example values in production.

Run the backend tests with `cd backend && mvn test`; run the frontend checks with `cd frontend && npm ci && npm run build && npm test -- --run`. Some integration tests require a running PostgreSQL or Testcontainers runtime; consult [docs/testing-strategy.md](docs/testing-strategy.md).

## Configuration and deployment

Use [docs/deployment.md](docs/deployment.md) and [render.yaml](render.yaml) for Compose and Render / Neon setup, storage, health checks, backup and release verification. `APP_ENV=production` and `SPRING_PROFILES_ACTIVE=prod` activate production validation. Gemini is optional; email delivery requires a working SMTP service in production. Never commit real environment files or keys.

Optional evaluation records are in [scripts/demo-data.sql](scripts/demo-data.sql); read [docs/demo-data.md](docs/demo-data.md) before running them in your own database.
