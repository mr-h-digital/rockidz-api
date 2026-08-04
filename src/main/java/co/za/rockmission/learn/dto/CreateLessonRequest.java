package co.za.rockmission.learn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLessonRequest(
        @NotBlank String title,
        @NotNull String videoProvider,   // YOUTUBE | CLOUDFLARE | BUNNY
        @NotBlank String videoRef,       // YouTube video ID for the YOUTUBE provider
        Integer durationSeconds,
        Integer orderIndex
) {}
