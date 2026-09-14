package co.za.rockmission.rockidz.dto;

public record LessonResponse(
        Long id,
        Long moduleId,
        String title,
        String contentType,
        String content,
        String instructions,
        String questions,
        String assetUrl,
        String downloadUrl,
        String gameType,
        String gamePrompt,
        String gameOptions,
        String gameAnswer,
        String successMessage,
        String retryMessage,
        String videoRef,
        Integer durationSeconds,
        Integer orderIndex
) {}
