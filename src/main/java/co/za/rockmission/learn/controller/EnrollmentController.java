package co.za.rockmission.learn.controller;

import co.za.rockmission.learn.dto.EnrollmentResponse;
import co.za.rockmission.learn.exception.ApiException;
import co.za.rockmission.learn.model.Course;
import co.za.rockmission.learn.model.Enrollment;
import co.za.rockmission.learn.model.User;
import co.za.rockmission.learn.repository.CourseRepository;
import co.za.rockmission.learn.repository.EnrollmentRepository;
import co.za.rockmission.learn.repository.LessonProgressRepository;
import co.za.rockmission.learn.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Students enroll themselves; this is also the endpoint the "Enroll" button on
 * learn.rockmission.co.za calls once a user is signed in (see ?courseSlug=&action=enroll
 * deep-link flow from the marketing site).
 */
@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;

    @PostMapping("/{courseSlug}")
    @PreAuthorize("hasAnyRole('STUDENT', 'EDUCATOR', 'ADMIN')")
    public ResponseEntity<EnrollmentResponse> enroll(
            @PathVariable String courseSlug,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseRepository.findBySlug(courseSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course not found: " + courseSlug));

        if (enrollmentRepository.existsByUserIdAndCourseId(currentUser.getId(), course.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Already enrolled in this course");
        }

        Enrollment enrollment = Enrollment.builder()
                .user(currentUser)
                .course(course)
                .status(Enrollment.EnrollmentStatus.ACTIVE)
                .build();

        enrollmentRepository.save(enrollment);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(enrollment));
    }

    /** "My courses" list for the student dashboard. */
    @GetMapping("/me")
    public List<EnrollmentResponse> myEnrollments(@AuthenticationPrincipal User currentUser) {
        return enrollmentRepository.findByUserId(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private EnrollmentResponse toResponse(Enrollment enrollment) {
        long totalLessons = enrollment.getCourse().getModules().stream()
                .mapToLong(m -> lessonRepository.findByModuleIdOrderByOrderIndexAsc(m.getId()).size())
                .sum();

        long completedLessons = lessonProgressRepository.countCompletedLessonsForCourse(
                enrollment.getUser().getId(), enrollment.getCourse().getId()
        );

        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getSlug(),
                enrollment.getCourse().getTitle(),
                enrollment.getStatus().name(),
                enrollment.getEnrolledAt(),
                completedLessons,
                totalLessons
        );
    }
}
