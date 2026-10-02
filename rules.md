# RULES.md — Clinic Management System

Read this file fully before every task. If a task conflicts with these rules, stop and ask.

## 1. Project summary

A web app for small/medium clinics. Doctors register patients, record visits with notes and medicines, and set the next visit date.

Current MVP scope (nothing else):

- Login with Google (allowlist only)
- Patients: create, search, view, edit
- Visits: create per patient, with notes
- Medicines per visit (name, dosage, frequency, duration)
- Next visit date on a visit + "Today's follow-ups" list
- Admin: manage allowed users and roles

Out of scope until told otherwise: billing, appointments calendar, inventory, lab reports, patient portal, SMS/WhatsApp, analytics.

## 2. Tech stack (do not add or swap libraries without approval)

- Backend: Java 21, Spring Boot 3.x, Spring Security (oauth2Login), Spring Data JPA, Flyway, PostgreSQL
- Frontend: React + TypeScript, Vite, React Router, TanStack Query, React Hook Form + Zod
- UI library: shadcn/ui + Tailwind (only this)
- Tests: JUnit 5 + Testcontainers (backend), Vitest + Testing Library (frontend)

## 3. Folder structure

```
/backend
  src/main/java/com/clinic/
    config/        security, CORS, app config
    auth/          oauth2 user service, /api/me
    admin/         user management
    patient/       controller, service, repository, entity, dto
    visit/
    medicine/
    common/        audit, exceptions, base classes
  src/main/resources/db/migration/   Flyway SQL files
/frontend
  src/
    api/           fetch wrappers only
    components/ui/ shadcn components (do not edit)
    components/    shared components
    features/      patients/, visits/, admin/, auth/
    pages/
    lib/
```

Each feature is self-contained. Do not create new top-level folders.

## 4. Backend rules

1. Layering: Controller → Service → Repository. No business logic in controllers. No repository calls from controllers.
2. Never expose JPA entities in API responses. Use DTOs (records).
3. Validate all input with Bean Validation on request DTOs.
4. Central error handling via one `@RestControllerAdvice`. Consistent error JSON.
5. Database changes only through Flyway migrations. Never edit an applied migration; add a new one.
6. Soft delete only for medical data (`deleted_at` column). Never hard delete.
7. Every table has `id`, `created_at`, `updated_at`, `created_by`.
8. Use constructor injection. No field `@Autowired`.
9. No secrets in code. Use environment variables.
10. Keep methods small. If a service exceeds ~300 lines, propose a split.

## 5. Security rules (highest priority)

1. Login is Google OAuth2 only. The email must exist in `app_user` and be active, and `email_verified` must be true.
2. Sessions are server-side with an HttpOnly, Secure, SameSite cookie. React never stores or handles tokens.
3. Admin email comes from `app.admin-email` (env variable). It is auto-created as ADMIN and can never be disabled or demoted.
4. Deny by default. Every endpoint requires authentication and an explicit role check on the server.
5. Roles: `ADMIN`, `DOCTOR`, `NURSE`, `RECEPTIONIST`. Receptionists cannot read clinical notes.
6. CSRF protection stays on. Frontend sends `X-XSRF-TOKEN` on POST/PUT/DELETE.
7. Audit log every login, and every create/update/delete/view of patient, visit, and medicine records. Logs are append-only.
8. Never log patient data (names, phones, notes) in application logs.
9. Idle session timeout: 30 minutes.
10. Use fake data only in dev. Never commit real patient data.

## 6. Frontend and UI rules

Principle: fewest clicks and least clutter wins. Build only what the task asks for.

1. Use only shadcn/ui components. No other UI libraries.
2. Use only theme colors defined in `tailwind.config` and CSS variables. Do not invent colors, gradients, shadows, or animations.
3. No decorative elements: no banners, illustrations, emojis, stat cards, or "welcome" sections unless the task requests them.
4. Every screen follows one layout: sidebar (nav) + page title + content. No extra panels.
5. Lists = one search box, one primary button, one table. Forms = only the fields specified.
6. Every data screen handles loading, empty, and error states.
7. All forms use React Hook Form + Zod, with inline field errors.
8. API calls only through `src/api/`. Components never call `fetch` directly.
9. Server state via TanStack Query. No global state library.
10. Include `credentials: "include"` and the CSRF header on all API calls. On 401, redirect to login.
11. Must work on tablet and desktop widths. Keyboard-friendly forms.
12. If a screen sketch or layout description is given, follow it exactly. Do not add sections.

## 7. Testing rules

- Every service method gets a unit test. Every endpoint gets an integration test covering: allowed role, denied role, unauthenticated.
- Frontend: test forms and key flows (create patient, add visit).
- Run all tests and the linter before declaring a task done.
- Do not delete or weaken existing tests to make them pass.

## 8. Git rules

- One branch per feature: `feature/<name>`.
- Small commits with clear messages (`feat: add patient search`).
- Never commit `.env`, credentials, or build output.

## 9. How the AI must work

1. Plan first. Before coding, list: files to create/modify, endpoints, tables, and UI components. Wait for approval.
2. Do only the task. No extra features, no refactoring unrelated code, no changing files outside the task.
3. Match existing patterns. Look at an existing feature and follow it.
4. No new dependencies without asking and explaining why.
5. Do not invent APIs or libraries. If unsure, say so.
6. When finished, report: what changed, how to run it, what tests were added, and anything left undone.
7. If something is ambiguous, ask one clear question instead of guessing.

## 10. Definition of done

- [ ] Works end to end (DB → API → UI)
- [ ] Role checks and audit logging in place
- [ ] Tests pass, linter clean
- [ ] Loading/empty/error states handled
- [ ] No unused code or files
- [ ] No UI elements beyond what was requested
- [ ] Committed on a feature branch

## 11. Periodic cleanup (every 3–4 features)

Ask the AI to: find duplicate code, dead files, unused dependencies, and inconsistent UI, then propose a refactor without changing behavior. Review before applying.
