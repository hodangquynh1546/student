# Development guide

The backend lives in `backend/` and uses Java 21, Maven, Spring Boot 3.x and PostgreSQL. The frontend lives in `frontend/` with React/TypeScript/Vite. Database schema is changed only by a new numbered file under `backend/src/main/resources/db/migration/`. Never edit a migration already applied to a shared database.

## Local workflow

1. Start a separate PostgreSQL database and set `DATABASE_URL` to its JDBC URL plus `DATABASE_USER` and `DATABASE_PASSWORD`.
2. Use a private local environment file based on `.env.example`. Set `SPRING_PROFILES_ACTIVE=dev` for local work. In production, provide a strong JWT key and admin bootstrap values; do not use placeholders.
3. Run backend tests from `backend/` with `mvn test`, then start the service with `mvn spring-boot:run`. The API docs should be available at `/swagger-ui.html` when the service runs.
4. Install locked frontend dependencies and start the Vite dev server. Its `/api` proxy should target the backend; refresh requests need `withCredentials: true`.
5. Before merging a slice, run migrations on a fresh database and test at least one allowed and one denied call for each role affected.

## Conventions

- Route prefix `/api/v1`; DTOs at the HTTP boundary; no JPA entity serialized to clients.
- Validate requests at the boundary and enforce business invariants in services and database constraints.
- Store timestamps in UTC and compare availability/deadlines with server time.
- Use paginated reads for growing lists and explicit DTO projections to avoid N+1.
- Preserve exam snapshots, enrollment history, grade changes and audit events; use soft delete where history matters.
- Update `docs/api-specification.md` when a contract changes, then coordinate frontend/backend updates together.
- Student import accepts `email,fullName` plus optional `phone,dateOfBirth,parentEmail`; use ISO dates. Check both per-row failures and invitation delivery when changing this path.
- Ranking and report calculations must draw only from eligible, published facts; keep report exports aligned with the stored snapshot.
- Add tests for business and authorization changes. Update `PROJECT_STATUS.md` with what actually passed.

## Code review checklist

For each sensitive endpoint, identify the actor from the JWT, the resource ownership query, the transaction boundary, the failure status and the data allowed in the response. For student-facing exam endpoints, inspect nested JSON for hidden answer markers. For parent-facing results, prove the link is accepted and unrevoked and the result is published. For file/AI features, check size limits, privacy, timeout and secret-free logging.
