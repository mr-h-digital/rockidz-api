package co.za.rockmission.rockidz.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCourseRequest(
        @NotBlank String slug,
        @NotBlank String title,
        String description,
        String thumbnailUrl
) {
}
