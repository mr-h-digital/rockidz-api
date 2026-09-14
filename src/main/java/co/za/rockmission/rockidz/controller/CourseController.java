package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.CourseSummaryResponse;
import co.za.rockmission.rockidz.dto.CreateCourseRequest;
import co.za.rockmission.rockidz.dto.FileUploadResponse;
import co.za.rockmission.rockidz.dto.RosterEntryResponse;
import co.za.rockmission.rockidz.dto.UpdateCourseRequest;
import co.za.rockmission.rockidz.exception.ApiException;
import co.za.rockmission.rockidz.model.Course;
import co.za.rockmission.rockidz.model.Enrollment;
import co.za.rockmission.rockidz.model.User;
import co.za.rockmission.rockidz.repository.CourseRepository;
import co.za.rockmission.rockidz.repository.EnrollmentRepository;
import co.za.rockmission.rockidz.repository.LessonProgressRepository;
import co.za.rockmission.rockidz.repository.LessonRepository;
import co.za.rockmission.rockidz.storage.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Public browsing is open (see SecurityConfig — GET is permitAll so the marketing
 * site / course catalog can be viewed without signing in). Creating/editing a course
 * is restricted to EDUCATOR or ADMIN.
 */
@RestController
@RequestMapping("/api/courses")
@Slf4j
@RequiredArgsConstructor
public class CourseController {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final Optional<StorageService> storageService;

    @GetMapping
    public List<CourseSummaryResponse> listPublished() {
        return courseRepository.findByStatus(Course.CourseStatus.PUBLISHED)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /** The signed-in educator's own courses (draft + published) — powers the Teach page. */
    @GetMapping("/mine")
    public List<CourseSummaryResponse> mine(@AuthenticationPrincipal User currentUser) {
        return courseRepository.findByCreatedById(currentUser.getId())
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @GetMapping("/{slug}")
    public CourseSummaryResponse getBySlug(@PathVariable String slug) {
        Course course = courseRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found: " + slug));
        return toSummary(course);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public ResponseEntity<CourseSummaryResponse> create(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (courseRepository.existsBySlug(request.slug())) {
            throw new ApiException(HttpStatus.CONFLICT, "A course with this slug already exists");
        }

        Course course = Course.builder()
                .slug(request.slug().toLowerCase().trim())
                .title(request.title())
                .description(request.description())
                .thumbnailUrl(request.thumbnailUrl())
                .status(Course.CourseStatus.DRAFT)
                .createdBy(currentUser)
                .build();

        courseRepository.save(course);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(course));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public CourseSummaryResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));

        assertOwnerOrAdmin(course.getId(), currentUser);

        String normalizedSlug = request.slug().toLowerCase().trim();
        if (!normalizedSlug.equals(course.getSlug()) && courseRepository.existsBySlug(normalizedSlug)) {
            throw new ApiException(HttpStatus.CONFLICT, "A course with this slug already exists");
        }

        course.setSlug(normalizedSlug);
        course.setTitle(request.title().trim());
        course.setDescription(request.description() == null ? null : request.description().trim());
        course.setThumbnailUrl(request.thumbnailUrl() == null ? null : request.thumbnailUrl().trim());
        courseRepository.save(course);
        return toSummary(course);
    }

    @PostMapping("/{id}/thumbnail")
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public ResponseEntity<FileUploadResponse> uploadThumbnail(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));

        assertOwnerOrAdmin(course.getId(), currentUser);

        StorageService.UploadedFile uploaded = requireStorageService().uploadCourseThumbnail(file, course.getId());
        course.setThumbnailUrl(uploaded.url());
        courseRepository.save(course);

        return ResponseEntity.ok(new FileUploadResponse(uploaded.url(), uploaded.key(), uploaded.contentType()));
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));

        if (course.getThumbnailUrl() == null || course.getThumbnailUrl().isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Course thumbnail not found");
        }

        StorageService.StoredFile storedFile = requireStorageService().fetchByUrl(course.getThumbnailUrl());
        MediaType mediaType = MediaType.parseMediaType(storedFile.contentType());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(storedFile.bytes());
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('EDUCATOR', 'ADMIN')")
    public CourseSummaryResponse publish(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));

        assertOwnerOrAdmin(course.getId(), currentUser);

        course.setStatus(Course.CourseStatus.PUBLISHED);
        courseRepository.save(course);
        return toSummary(course);
    }

    /** Educator's view of everyone enrolled in one of their courses, with progress. */
    @GetMapping("/{id}/roster")
    public List<RosterEntryResponse> roster(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found"));

        assertOwnerOrAdmin(course.getId(), currentUser);

        long totalLessons = lessonRepository.countByCourseId(id);

        return enrollmentRepository.findByCourseId(id).stream()
                .map(e -> toRosterEntry(e, totalLessons))
                .toList();
    }

    private RosterEntryResponse toRosterEntry(Enrollment enrollment, long totalLessons) {
        User student = enrollment.getUser();
        long completedLessons = lessonProgressRepository.countCompletedLessonsForCourse(
                student.getId(), enrollment.getCourse().getId()
        );

        return new RosterEntryResponse(
                student.getId(),
                student.getDisplayName(),
                student.getEmail(),
                enrollment.getStatus().name(),
                enrollment.getEnrolledAt(),
                completedLessons,
                totalLessons
        );
    }

    /** Only the educator who created the course (or an admin) may modify it. */
    private void assertOwnerOrAdmin(Long courseId, User currentUser) {
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isOwner = courseRepository.existsByIdAndCreatedById(courseId, currentUser.getId());

        if (!isOwner && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not own this course");
        }
    }

    private StorageService requireStorageService() {
        return storageService.orElseThrow(() -> new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "File uploads are not configured yet. Please add the STORAGE_* variables in Railway first."));
    }

    private CourseSummaryResponse toSummary(Course course) {
        long enrolledCount = enrollmentRepository.countByCourseId(course.getId());
        return new CourseSummaryResponse(
                course.getId(),
                course.getSlug(),
                course.getTitle(),
                course.getDescription(),
                course.getThumbnailUrl() == null || course.getThumbnailUrl().isBlank()
                        ? null
                        : "/api/courses/" + course.getId() + "/thumbnail",
                course.getStatus().name(),
                course.getCreatedBy().getDisplayName(),
                enrolledCount
        );
    }
}
