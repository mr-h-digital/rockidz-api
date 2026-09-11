package co.za.rockmission.rockidz.dto;

import java.time.Instant;

public record EnrollmentResponse(
        Long enrollmentId,
        Long courseId,
        String courseSlug,
        String courseTitle,
        String status,
        Instant enrolledAt,
        long completedLessons,
        long totalLessons
) {}
