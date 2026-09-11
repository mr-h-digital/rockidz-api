package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.CreateLessonRequest;
import co.za.rockmission.rockidz.dto.LessonResponse;
import co.za.rockmission.rockidz.exception.ApiException;
import co.za.rockmission.rockidz.model.Lesson;
import co.za.rockmission.rockidz.model.Module;
import co.za.rockmission.rockidz.model.User;
import co.za.rockmission.rockidz.repository.LessonRepository;
import co.za.rockmission.rockidz.repository.ModuleRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Lesson authoring, nested under a module. Same ownership rule as ModuleController:
 * only the course's creator (or an admin) may add/remove lessons.
 */
@RestController
@RequestMapping("/api/modules/{moduleId}/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    @GetMapping
    public List<LessonResponse> list(@PathVariable Long moduleId) {
        return lessonRepository.findByModuleIdOrderByOrderIndexAsc(moduleId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public ResponseEntity<LessonResponse> create(
            @PathVariable Long moduleId,
            @Valid @RequestBody CreateLessonRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Module not found"));

        assertOwnerOrAdmin(module, currentUser);

        Lesson lesson = Lesson.builder()
                .module(module)
                .title(request.title())
                .videoProvider(Lesson.VideoProvider.valueOf(request.videoProvider().toUpperCase()))
                .videoRef(request.videoRef())
                .durationSeconds(request.durationSeconds())
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : nextOrderIndex(moduleId))
                .build();

        lessonRepository.save(lesson);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(lesson));
    }

    @DeleteMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long moduleId,
            @PathVariable Long lessonId,
            @AuthenticationPrincipal User currentUser
    ) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Module not found"));
        assertOwnerOrAdmin(module, currentUser);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lesson not found"));

        lessonRepository.delete(lesson);
        return ResponseEntity.noContent().build();
    }

    private int nextOrderIndex(Long moduleId) {
        return lessonRepository.findByModuleIdOrderByOrderIndexAsc(moduleId).size();
    }

    private void assertOwnerOrAdmin(Module module, User currentUser) {
        boolean isOwner = module.getCourse().getCreatedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole().name().equals("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not own this course");
        }
    }

    private LessonResponse toResponse(Lesson lesson) {
        return new LessonResponse(
                lesson.getId(), lesson.getModule().getId(), lesson.getTitle(),
                lesson.getVideoProvider().name(), lesson.getVideoRef(),
                lesson.getDurationSeconds(), lesson.getOrderIndex()
        );
    }
}
