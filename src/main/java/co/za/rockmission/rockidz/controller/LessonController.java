package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.CreateLessonRequest;
import co.za.rockmission.rockidz.dto.FileUploadResponse;
import co.za.rockmission.rockidz.dto.LessonResponse;
import co.za.rockmission.rockidz.dto.UpdateLessonRequest;
import co.za.rockmission.rockidz.exception.ApiException;
import co.za.rockmission.rockidz.model.Lesson;
import co.za.rockmission.rockidz.model.Module;
import co.za.rockmission.rockidz.model.User;
import co.za.rockmission.rockidz.repository.CourseRepository;
import co.za.rockmission.rockidz.repository.LessonRepository;
import co.za.rockmission.rockidz.repository.ModuleRepository;
import co.za.rockmission.rockidz.storage.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Lesson authoring, nested under a module. Same ownership rule as ModuleController:
 * only the course's creator (or an admin) may add/remove lessons.
 */
@RestController
@RequestMapping("/api/modules/{moduleId}/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final Optional<StorageService> storageService;

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

        assertOwnerOrAdmin(module.getCourse().getId(), currentUser);

        Lesson lesson = Lesson.builder()
                .module(module)
                .title(request.title().trim())
                .contentType(Lesson.ContentType.valueOf(request.contentType().toUpperCase().trim()))
                .content(trimToNull(request.content()))
                .instructions(trimToNull(request.instructions()))
                .questions(trimToNull(request.questions()))
                .assetUrl(trimToNull(request.assetUrl()))
                .downloadUrl(trimToNull(request.downloadUrl()))
                .gameType(trimToNull(request.gameType()))
                .gamePrompt(trimToNull(request.gamePrompt()))
                .gameOptions(trimToNull(request.gameOptions()))
                .gameAnswer(trimToNull(request.gameAnswer()))
                .successMessage(trimToNull(request.successMessage()))
                .retryMessage(trimToNull(request.retryMessage()))
                .videoRef(trimToNull(request.videoRef()))
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
        assertOwnerOrAdmin(module.getCourse().getId(), currentUser);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lesson not found"));

        lessonRepository.delete(lesson);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public LessonResponse update(
            @PathVariable Long moduleId,
            @PathVariable Long lessonId,
            @Valid @RequestBody UpdateLessonRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Module not found"));
        assertOwnerOrAdmin(module.getCourse().getId(), currentUser);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lesson not found"));
        if (!lesson.getModule().getId().equals(moduleId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lesson does not belong to this module");
        }

        lesson.setTitle(request.title().trim());
        lesson.setContentType(Lesson.ContentType.valueOf(request.contentType().toUpperCase().trim()));
        lesson.setContent(trimToNull(request.content()));
        lesson.setInstructions(trimToNull(request.instructions()));
        lesson.setQuestions(trimToNull(request.questions()));
        lesson.setAssetUrl(trimToNull(request.assetUrl()));
        lesson.setDownloadUrl(trimToNull(request.downloadUrl()));
        lesson.setGameType(trimToNull(request.gameType()));
        lesson.setGamePrompt(trimToNull(request.gamePrompt()));
        lesson.setGameOptions(trimToNull(request.gameOptions()));
        lesson.setGameAnswer(trimToNull(request.gameAnswer()));
        lesson.setSuccessMessage(trimToNull(request.successMessage()));
        lesson.setRetryMessage(trimToNull(request.retryMessage()));
        lesson.setVideoRef(trimToNull(request.videoRef()));
        lesson.setDurationSeconds(request.durationSeconds());
        lessonRepository.save(lesson);
        return toResponse(lesson);
    }

    @PostMapping("/{lessonId}/asset")
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public ResponseEntity<FileUploadResponse> uploadAsset(
            @PathVariable Long moduleId,
            @PathVariable Long lessonId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser
    ) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Module not found"));
        assertOwnerOrAdmin(module.getCourse().getId(), currentUser);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lesson not found"));
        if (!lesson.getModule().getId().equals(moduleId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lesson does not belong to this module");
        }

        StorageService.UploadedFile uploaded = requireStorageService().uploadLessonAssetImage(file, lesson.getId());
        lesson.setAssetUrl(uploaded.url());
        lessonRepository.save(lesson);

        return ResponseEntity.ok(new FileUploadResponse(uploaded.url(), uploaded.key(), uploaded.contentType()));
    }

    private int nextOrderIndex(Long moduleId) {
        return lessonRepository.findByModuleIdOrderByOrderIndexAsc(moduleId).size();
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

    private LessonResponse toResponse(Lesson lesson) {
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

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private StorageService requireStorageService() {
        return storageService.orElseThrow(() -> new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "File uploads are not configured yet. Please add the STORAGE_* variables in Railway first."));
    }
}
