-- ============================================================
-- Rock Mission Ministries - Bible Study Learning Platform
-- V1: Core schema (auth, courses, structure, enrollment, progress)
-- ============================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(150) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'STUDENT', -- STUDENT | EDUCATOR | ADMIN
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE courses (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(160) NOT NULL UNIQUE,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    thumbnail_url   VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT', -- DRAFT | PUBLISHED | ARCHIVED
    created_by      BIGINT       NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE modules (
    id              BIGSERIAL PRIMARY KEY,
    course_id       BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    order_index     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE lessons (
    id              BIGSERIAL PRIMARY KEY,
    module_id       BIGINT       NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    video_provider  VARCHAR(20)  NOT NULL DEFAULT 'YOUTUBE', -- YOUTUBE | CLOUDFLARE | BUNNY
    video_ref       VARCHAR(300) NOT NULL,                   -- e.g. YouTube video ID
    duration_seconds INTEGER,
    order_index     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE enrollments (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id       BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | COMPLETED | DROPPED
    enrolled_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    UNIQUE (user_id, course_id)
);

CREATE TABLE lesson_progress (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id           BIGINT      NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    watch_time_seconds  INTEGER     NOT NULL DEFAULT 0,
    completed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, lesson_id)
);

-- Helpful indexes for the dashboard / roster queries
CREATE INDEX idx_courses_created_by ON courses(created_by);
CREATE INDEX idx_modules_course_id ON modules(course_id);
CREATE INDEX idx_lessons_module_id ON lessons(module_id);
CREATE INDEX idx_enrollments_user_id ON enrollments(user_id);
CREATE INDEX idx_enrollments_course_id ON enrollments(course_id);
CREATE INDEX idx_lesson_progress_user_id ON lesson_progress(user_id);
