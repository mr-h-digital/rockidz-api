package co.za.rockmission.learn.controller;

import co.za.rockmission.learn.dto.CreateModuleRequest;
import co.za.rockmission.learn.dto.LessonResponse;
import co.za.rockmission.learn.dto.ModuleResponse;
import co.za.rockmission.learn.exception.ApiException;
import co.za.rockmission.learn.model.Course;
import co.za.rockmission.learn.model.Lesson;
import co.za.rockmission.learn.model.Module;
import co.za.rockmission.learn.model.User;
import co.za.rockmission.learn.repository.CourseRepository;
import co.za.rockmission.learn.repository.LessonRepository;
import co.za.rockmission.learn.repository.ModuleRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module authoring, nested under a course. Only the educator who owns the course
 * (or an admin) may add/reorder modules. Reading modules (with their lessons) is
 * public so the course preview page can show a syllabus before enrolling.
 */
@RestController
@RequestMapping("/api/courses/{courseId}/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    @GetMapping
    public List<ModuleResponse> list(@PathVariable Long courseId) {
        return moduleRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public ResponseEntity<ModuleResponse> create(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateModuleRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));

        assertOwnerOrAdmin(course, currentUser);

        Module module = Module.builder()
                .course(course)
                .title(request.title())
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : nextOrderIndex(courseId))
                .build();

        moduleRepository.save(module);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(module));
    }

    @DeleteMapping("/{moduleId}")
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));
        assertOwnerOrAdmin(course, currentUser);

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Module not found"));

        moduleRepository.delete(module); // cascades to lessons (see V1 schema FK ON DELETE CASCADE)
        return ResponseEntity.noContent().build();
    }

    private int nextOrderIndex(Long courseId) {
        return moduleRepository.findByCourseIdOrderByOrderIndexAsc(courseId).size();
    }

    private void assertOwnerOrAdmin(Course course, User currentUser) {
        boolean isOwner = course.getCreatedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole().name().equals("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not own this course");
        }
    }

    private ModuleResponse toResponse(Module module) {
        List<LessonResponse> lessons = lessonRepository.findByModuleIdOrderByOrderIndexAsc(module.getId())
                .stream()
                .map(this::toLessonResponse)
                .toList();

        return new ModuleResponse(module.getId(), module.getCourse().getId(), module.getTitle(),
                module.getOrderIndex(), lessons);
    }

    private LessonResponse toLessonResponse(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(), lesson.getModule().getId(), lesson.getTitle(),
                lesson.getVideoProvider().name(), lesson.getVideoRef(),
                lesson.getDurationSeconds(), lesson.getOrderIndex()
        );
    }
}
