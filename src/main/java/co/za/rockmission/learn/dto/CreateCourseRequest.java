package co.za.rockmission.learn.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCourseRequest(
        @NotBlank String slug,
        @NotBlank String title,
        String description,
        String thumbnailUrl
) {}
