package co.za.rockmission.learn.dto;

import java.time.Instant;

public record LessonProgressResponse(
        Long lessonId,
        Integer watchTimeSeconds,
        Instant completedAt
) {}
