package co.za.rockmission.learn.dto;

public record CourseSummaryResponse(
        Long id,
        String slug,
        String title,
        String description,
        String thumbnailUrl,
        String status,
        String createdByName,
        long enrolledCount
) {}
