package co.za.rockmission.rockidz.dto;

import java.time.Instant;

public record LessonProgressResponse(
        Long lessonId,
        Integer watchTimeSeconds,
        Instant completedAt
) {}
