package co.za.rockmission.learn.controller;

import co.za.rockmission.learn.dto.CourseSummaryResponse;
import co.za.rockmission.learn.dto.CreateCourseRequest;
import co.za.rockmission.learn.dto.RosterEntryResponse;
import co.za.rockmission.learn.exception.ApiException;
import co.za.rockmission.learn.model.Course;
import co.za.rockmission.learn.model.Enrollment;
import co.za.rockmission.learn.model.User;
import co.za.rockmission.learn.repository.CourseRepository;
import co.za.rockmission.learn.repository.EnrollmentRepository;
import co.za.rockmission.learn.repository.LessonProgressRepository;
import co.za.rockmission.learn.repository.LessonRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

        long totalLessons = course.getModules().stream()
                .mapToLong(m -> lessonRepository.findByModuleIdOrderByOrderIndexAsc(m.getId()).size())
                .sum();

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

                log.info("Course authz check: courseId={}, userId={}, email={}, isAdmin={}, isOwner={}",
                                courseId, currentUser.getId(), currentUser.getEmail(), isAdmin, isOwner);

        if (!isOwner && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not own this course");
        }
    }

    private CourseSummaryResponse toSummary(Course course) {
        long enrolledCount = enrollmentRepository.countByCourseId(course.getId());
        return new CourseSummaryResponse(
                course.getId(),
                course.getSlug(),
                course.getTitle(),
                course.getDescription(),
                course.getThumbnailUrl(),
                course.getStatus().name(),
                course.getCreatedBy().getDisplayName(),
                enrolledCount
        );
    }
}
