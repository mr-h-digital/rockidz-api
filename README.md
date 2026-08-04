# Rock Mission Ministries — Bible Study Learning API

Spring Boot (Maven) backend for the Rock Mission Ministries Bible study learning
platform. Serves a React SPA hosted on GitHub Pages (`learn.rockmission.co.za`)
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

Ownership rule throughout: only the educator who created a course (or an admin)
can author modules/lessons on it or view its roster — enforced in each controller,
not just at the route level.

Not yet built (planned for later phases, per the roadmap): quizzes, certificates,
badges, Q&A/community.

## Local setup

1. Create a local Postgres database, e.g.:
   ```bash
   createdb rockmission_learn
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
   `jdbc:postgresql://localhost:5432/rockmission_learn` with `postgres`/`postgres`.
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
   - `SPRING_DATASOURCE_URL` — `jdbc:postgresql://<PGHOST>:<PGPORT>/<PGDATABASE>`
     (build this from the Postgres plugin's connection details, or reference
     them directly if Railway exposes `PGHOST`/`PGPORT`/`PGDATABASE` as
     variables you can interpolate)
   - `SPRING_DATASOURCE_USERNAME` — from the Postgres plugin
   - `SPRING_DATASOURCE_PASSWORD` — from the Postgres plugin
   - `JWT_SECRET` — a long random string (32+ characters). Generate one with
     `openssl rand -base64 48`, don't reuse the placeholder in `application.yml`.
   - `CORS_ALLOWED_ORIGINS` — `https://learn.rockmission.co.za` (add
     `http://localhost:5173` too while developing the React app locally)
4. Railway sets `PORT` automatically — `application.yml` already reads
   `${PORT:8080}`, so no change needed there.
5. On first deploy, Flyway will run `V1__init_core_schema.sql` against the
   Railway Postgres instance automatically.

## Next steps

- Quizzes (MCQ) and certificate generation on course completion
- Badges (rules-based, triggered off enrollment/progress updates)
- Scaffold the React frontend against this API
- Point `learn.rockmission.co.za` (CNAME) at the GitHub Pages app once it exists
