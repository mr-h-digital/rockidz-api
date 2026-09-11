package co.za.rockmission.rockidz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateLessonRequest(
        @NotBlank String title,
        @NotNull String videoProvider,
        @NotBlank String videoRef,
        Integer durationSeconds
) {
}
