package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.CreateModuleRequest;
import co.za.rockmission.rockidz.dto.LessonResponse;
import co.za.rockmission.rockidz.dto.ModuleResponse;
import co.za.rockmission.rockidz.dto.UpdateModuleRequest;
import co.za.rockmission.rockidz.exception.ApiException;
import co.za.rockmission.rockidz.model.Course;
import co.za.rockmission.rockidz.model.Lesson;
import co.za.rockmission.rockidz.model.Module;
import co.za.rockmission.rockidz.model.User;
import co.za.rockmission.rockidz.repository.CourseRepository;
import co.za.rockmission.rockidz.repository.LessonRepository;
import co.za.rockmission.rockidz.repository.ModuleRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ModuleResponse> create(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateModuleRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));

        assertOwnerOrAdmin(course.getId(), currentUser);

        Module module = Module.builder()
                .course(course)
                .title(request.title())
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : nextOrderIndex(courseId))
                .build();

        moduleRepository.save(module);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(module));
    }

    @DeleteMapping("/{moduleId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));
        assertOwnerOrAdmin(course.getId(), currentUser);

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Module not found"));

        moduleRepository.delete(module); // cascades to lessons (see V1 schema FK ON DELETE CASCADE)
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{moduleId}")
    public ModuleResponse update(
            @PathVariable Long courseId,
            @PathVariable Long moduleId,
            @Valid @RequestBody UpdateModuleRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));
        assertOwnerOrAdmin(course.getId(), currentUser);

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Module not found"));
        if (!module.getCourse().getId().equals(courseId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Module does not belong to this course");
        }

        module.setTitle(request.title().trim());
        moduleRepository.save(module);
        return toResponse(module);
    }

    private int nextOrderIndex(Long courseId) {
        return moduleRepository.findByCourseIdOrderByOrderIndexAsc(courseId).size();
    }

        private void assertOwnerOrAdmin(Long courseId, User currentUser) {
                if (currentUser == null) {
                        throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
                }

                boolean isOwner = courseRepository.existsByIdAndCreatedById(courseId, currentUser.getId());
                boolean isAdmin = currentUser.getAuthorities().stream()
                                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

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
                lesson.getContentType().name(), lesson.getContent(),
                lesson.getInstructions(), lesson.getQuestions(),
                lesson.getAssetUrl(), lesson.getDownloadUrl(),
                lesson.getGameType(), lesson.getGamePrompt(), lesson.getGameOptions(),
                lesson.getGameAnswer(), lesson.getSuccessMessage(), lesson.getRetryMessage(),
                lesson.getVideoRef(),
                lesson.getDurationSeconds(), lesson.getOrderIndex()
        );
    }
}
