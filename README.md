# Rock Mission Ministries — Kids Corner Learning API

Spring Boot (Maven) backend for the Rock Mission Ministries Bible study learning
platform. Serves a React SPA hosted on GitHub Pages (`rockidz.rockmission.co.za`)
and persists to PostgreSQL on Railway.

## Stack

- Java 21, Spring Boot 3.5.8
- Spring Security + JWT (stateless auth, no sessions/cookies — safe across the
  GitHub Pages ↔ Railway origin split)
- Spring Data JPA + PostgreSQL
- Flyway for schema migrations (`src/main/resources/db/migration`)

## What's built so far (Sprint 1 + 2)

- Core schema: `users`, `courses`, `modules`, `lessons`, `enrollments`, `lesson_progress`
- Auth: `POST /api/auth/signup`, `POST /api/auth/login` (returns a JWT)
- Password recovery: `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`
- Courses: public `GET /api/courses` / `GET /api/courses/{slug}`, educator-only
  `POST /api/courses`, `PATCH /api/courses/{id}/publish`, and `GET /api/courses/mine`
  (the signed-in educator's own draft + published courses, filtered by `createdBy.id`)
- Course authoring: educator-only `POST /api/courses/{courseId}/modules`,
  `DELETE /api/courses/{courseId}/modules/{moduleId}`, `POST /api/modules/{moduleId}/lessons`,
  `DELETE /api/modules/{moduleId}/lessons/{lessonId}`; public `GET` on both for the
  course-preview/syllabus page
- Enrollment: `POST /api/enrollments/{courseSlug}`, `GET /api/enrollments/me`
- Progress tracking: `PUT /api/lessons/{lessonId}/progress` — records watch time,
  marks a lesson complete, and auto-flips the enrollment to `COMPLETED` once every
  lesson in the course is done (this is the hook point for certificate generation later)
- Educator roster: `GET /api/courses/{id}/roster` — enrolled students plus each one's
  completed/total lesson counts, owner-checked so an educator only sees their own courses
- Admin role management: `PATCH /api/admin/users/role` (admin-only) — promote
  registered users to `EDUCATOR` (or change to `ADMIN`/`STUDENT`) by email
- Profile settings: `GET /api/users/me`, `PATCH /api/users/me` (display name,
  email, and optional password change with current-password verification)

Ownership rule throughout: only the educator who created a course (or an admin)
can author modules/lessons on it or view its roster — enforced in each controller,
not just at the route level.

## Promoting a user to educator

1. Ensure the caller is signed in as an `ADMIN` user.
2. Call:

   ```bash
   curl -X PATCH https://rockidz-api.rockmission.co.za/api/admin/users/role \
     -H "Authorization: Bearer <ADMIN_JWT>" \
     -H "Content-Type: application/json" \
     -d '{"email":"educator@example.com","role":"EDUCATOR"}'
   ```

3. Ask that user to sign out and sign in again so their JWT contains the new role.

## Password reset notes

- `POST /api/auth/forgot-password` always returns a generic success message.
- For environments without email infrastructure yet, set
  `EXPOSE_RESET_TOKEN=true` to include a temporary reset URL/token in the API
  response so the frontend flow can still be tested.
- Keep `EXPOSE_RESET_TOKEN=false` in production once SMTP/email delivery is in place.

Not yet built (planned for later phases, per the roadmap): quizzes, certificates,
badges, Q&A/community.

## Local setup

1. Create a local Postgres database, e.g.:
   ```bash
   createdb rockidz
   ```
2. Run the app (Flyway will apply migrations automatically on startup). If you
   have Maven installed locally:
   ```bash
   mvn spring-boot:run
   ```
   No Maven installed? Generate the wrapper once (`.mvn/wrapper/maven-wrapper.properties`
   is already included) with `mvn -N wrapper:wrapper`, then use `./mvnw spring-boot:run`
   from then on.
   Default local config (see `application.yml`) points at
   `jdbc:postgresql://localhost:5432/rockidz` with `postgres`/`postgres`.
   Override via env vars if your local setup differs.
3. Try it:
   ```bash
   curl -X POST http://localhost:8080/api/auth/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"student@example.com","password":"changeme123","displayName":"Test Student"}'
   ```

## Deploying to Railway

1. Create a new Railway project, add a **PostgreSQL** plugin.
2. Add a service from this repo (Railway auto-detects the Maven build via
   `pom.xml` — no Dockerfile needed for a standard Spring Boot app).
3. Set these environment variables on the service (Railway → Variables):
   - Preferred: link the Postgres plugin so Railway injects `PGHOST`, `PGPORT`,
     `PGDATABASE`, `PGUSER`, `PGPASSWORD`.
   - Also supported: Railway's `POSTGRES_DB`, `POSTGRES_USER`,
     `POSTGRES_PASSWORD` variables alongside `PGHOST` / `PGPORT`.
   - Optional explicit override: set `SPRING_DATASOURCE_URL`,
     `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
   - If your Railway setup exposes only `DATABASE_URL` (postgres://...), the app
     auto-converts it to Spring JDBC settings at startup.
   - `JWT_SECRET` — a long random string (32+ characters). Generate one with
     `openssl rand -base64 48`, don't reuse the placeholder in `application.yml`.
   - Optional admin bootstrap:
     `ADMIN_BOOTSTRAP_ENABLED=true`, `ADMIN_EMAIL=admin@rockmission.co.za`,
     `ADMIN_PASSWORD=<strong password>`, optional `ADMIN_DISPLAY_NAME`
   - `CORS_ALLOWED_ORIGINS` — `https://rockidz.rockmission.co.za` (add
     `http://localhost:5173` too while developing the React app locally)
   - File storage (Railway S3-compatible bucket):
     `STORAGE_ENDPOINT_URL`, `STORAGE_REGION`, `STORAGE_BUCKET_NAME`,
     `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY`
     or the Railway bucket variable names:
     `ENDPOINT`, `REGION`, `BUCKET`, `ACCESS_KEY_ID`, `SECRET_ACCESS_KEY`
   - Optional file CDN/public URL override: `STORAGE_PUBLIC_BASE_URL`
     or `PUBLIC_BASE_URL`
4. Railway sets `PORT` automatically — `application.yml` already reads
   `${PORT:8080}`, so no change needed there.
5. On first deploy, Flyway will run `V1__init_core_schema.sql` against the
   Railway Postgres instance automatically.

If admin bootstrap is enabled, startup will create the admin user if it does
not exist yet, or promote/update that user to `ADMIN` if the email already
exists.

## File uploads

- `POST /api/users/me/avatar` — authenticated avatar upload (`multipart/form-data`, field name: `file`)
- `POST /api/admin/uploads/downloadables` — admin/educator upload for downloadable kids content (`multipart/form-data`, field name: `file`)

## Next steps

- Quizzes (MCQ) and certificate generation on course completion
- Badges (rules-based, triggered off enrollment/progress updates)
- Scaffold the React frontend against this API
- Point `rockidz.rockmission.co.za` (CNAME) at the GitHub Pages app once it exists
