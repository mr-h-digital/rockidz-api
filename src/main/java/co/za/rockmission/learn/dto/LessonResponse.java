package co.za.rockmission.learn.dto;

public record LessonResponse(
        Long id,
        Long moduleId,
        String title,
        String videoProvider,
        String videoRef,
        Integer durationSeconds,
        Integer orderIndex
) {}
