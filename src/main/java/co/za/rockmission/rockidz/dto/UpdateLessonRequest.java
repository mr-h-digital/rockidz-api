package co.za.rockmission.rockidz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateLessonRequest(
        @NotBlank String title,
        @NotNull String contentType,
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
        Integer durationSeconds
) {
}
