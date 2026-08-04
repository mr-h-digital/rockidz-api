package co.za.rockmission.learn.dto;

import java.util.List;

public record ModuleResponse(
        Long id,
        Long courseId,
        String title,
        Integer orderIndex,
        List<LessonResponse> lessons
) {}
