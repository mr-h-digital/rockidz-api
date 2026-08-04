package co.za.rockmission.learn.dto;

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
