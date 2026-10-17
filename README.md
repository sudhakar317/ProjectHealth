# Clinic — backend

The API for a small-clinic management system: Google sign-in for an allow-listed staff, patients,
visits with notes and follow-up dates, and an append-only audit log. The web client lives in the
sibling repository [`ProjectHealthClient`](https://github.com/sudhakar317/ProjectHealthClient).

Java 21 · Spring Boot 3.4 · Spring Security (OAuth2 login) · Spring Data JPA · Flyway · PostgreSQL

## Prerequisites

- **JDK 21.** Maven is not needed: use the wrapper, `./mvnw` (or `mvnw.cmd` on Windows).
- **PostgreSQL 17.** 18 works, but Flyway logs that it has not been tested against it.

## Running locally

1. Create the two databases:

   ```sql
   CREATE DATABASE clinic;
   CREATE DATABASE clinic_test;
   ```

2. Create `src/main/resources/application-local.properties`. It is git-ignored and holds secrets, so
   it never leaves your machine:

   ```properties
   spring.datasource.password=<your postgres password>
   spring.security.oauth2.client.registration.google.client-id=<client id>
   spring.security.oauth2.client.registration.google.client-secret=<client secret>

   # Plain http locally, where a Secure cookie would never be sent back.
   server.servlet.session.cookie.secure=false

   # Optional: sign in by email alone, for testing without Google. See "Dev login" below.
   app.dev-login.enabled=true
   ```

3. Start it with the `local` profile:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

   The API listens on `http://localhost:8080`. Start the client and open `http://localhost:5173`.

### Testing on a phone

The client listens on every interface, so a phone on the same network can open
`http://<this machine's IP>:5173`. Two things are needed:

- Start the backend with `FRONTEND_URL=http://<IP>:5173`, or sign-in redirects land on `localhost`.
- On Windows, allow inbound TCP 5173 through the firewall (Public networks block it by default).

Google refuses `http` redirect URIs for anything but `localhost`, so on a phone use the dev login.

## Tests

The integration tests run against the real `clinic_test` database:

```bash
DB_PASSWORD=<your postgres password> ./mvnw verify
```

`DB_USER` defaults to `postgres`; set `TEST_DB_URL` to point somewhere else. CI runs the same
command against a PostgreSQL service container (`.github/workflows/ci.yml`).

## Configuration

Every secret comes from the environment. Nothing below has a usable default in production.

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL. Default `jdbc:postgresql://localhost:5432/clinic` |
| `DB_USER` / `DB_PASSWORD` | Database credentials. The password has no default on purpose |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | OAuth2 client. No default |
| `ADMIN_EMAIL` | The protected admin: created on startup, can never be disabled or demoted |
| `FRONTEND_URL` | Where to send the browser after sign-in. Default `http://localhost:5173` |
| `SESSION_COOKIE_SECURE` | Default `true`. Only turn off for plain-http local development |

## Behaviour worth knowing

**Errors have one shape.** Every failure, including ones rejected before a controller runs, returns:

```json
{ "message": "This patient already has a visit on that date",
  "fieldErrors": { "visitDate": "This patient already has a visit on that date" },
  "requestId": "ab12cd34ef56" }
```

Messages are written for the person using the app. An unexpected fault returns a generic 500 and
never its detail; the detail is logged under the same `requestId`, which is also sent as the
`X-Request-Id` header and printed on every log line.

**Health check.** `GET /api/health` is open to anyone and answers only `UP` or `DOWN` (503 when the
database is unreachable), for a load balancer or uptime monitor.

**Dev login.** `POST /api/dev/login` signs in by email with no password. It exists only when
`app.dev-login.enabled=true`, and the app **refuses to start** if that is set outside the `local`
profile, so it cannot be left on in production by accident.

**Sessions** are server-side, `HttpOnly`, and expire after 30 minutes idle.

**Clinical records are never deleted.** Patients and visits are soft-deleted and can be restored.
A doctor who has patients or visits cannot be deleted either; deactivate them instead.

**Audit log.** Every sign-in, and every create, update, delete and view of a patient or visit, is
recorded. The table rejects `UPDATE` and `DELETE` at the database level. Entries carry ids only:
names, phone numbers and notes never reach the log.

## Deploying

- Serve the client and API from the **same origin** (proxy `/api`, `/oauth2`, `/login` and `/logout`
  to this service). The session cookie and CSRF token depend on it.
- Terminate HTTPS in front of the app. `server.forward-headers-strategy=framework` is set, so
  forwarded headers are trusted for building redirect URIs; make sure your proxy sets them and
  strips any sent by clients.
- Run with the default profile. Do not set `app.dev-login.enabled`.
