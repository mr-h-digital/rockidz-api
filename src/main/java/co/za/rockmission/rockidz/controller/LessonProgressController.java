package co.za.rockmission.rockidz.controller;

import co.za.rockmission.rockidz.dto.LessonProgressResponse;
import co.za.rockmission.rockidz.dto.UpdateProgressRequest;
import co.za.rockmission.rockidz.exception.ApiException;
import co.za.rockmission.rockidz.model.*;
import co.za.rockmission.rockidz.repository.EnrollmentRepository;
import co.za.rockmission.rockidz.repository.LessonProgressRepository;
import co.za.rockmission.rockidz.repository.LessonRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Called by the course player as a student watches a lesson — periodic watch-time
 * pings, plus a final "mark complete" once the completion threshold (e.g. 90%
 * watched) is reached client-side. This is what the student dashboard's progress
 * bars and the "hours studied" badge criteria will read from.
 */
@RestController
@RequestMapping("/api/lessons/{lessonId}/progress")
@RequiredArgsConstructor
public class LessonProgressController {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;

    @PutMapping
    public LessonProgressResponse updateProgress(
            @PathVariable Long lessonId,
            @Valid @RequestBody UpdateProgressRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal User currentUser
    ) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lesson not found"));

        Long courseId = lesson.getModule().getCourse().getId();
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(currentUser.getId(), courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You are not enrolled in this course"));

        LessonProgress progress = lessonProgressRepository
                .findByUserIdAndLessonId(currentUser.getId(), lessonId)
                .orElseGet(() -> LessonProgress.builder()
                        .user(currentUser)
                        .lesson(lesson)
                        .watchTimeSeconds(0)
                        .build());

        // Watch time only ever moves forward — a stale/out-of-order ping shouldn't erase progress.
        progress.setWatchTimeSeconds(Math.max(progress.getWatchTimeSeconds(), request.watchTimeSeconds()));

        if (request.markComplete() && progress.getCompletedAt() == null) {
            progress.setCompletedAt(Instant.now());
        }

        lessonProgressRepository.save(progress);

        maybeCompleteEnrollment(enrollment, courseId, currentUser.getId());

        return new LessonProgressResponse(lessonId, progress.getWatchTimeSeconds(), progress.getCompletedAt());
    }

    /** Flip the enrollment to COMPLETED once every lesson in the course has been finished. */
    private void maybeCompleteEnrollment(Enrollment enrollment, Long courseId, Long userId) {
        if (enrollment.getStatus() == Enrollment.EnrollmentStatus.COMPLETED) {
            return;
        }

        long totalLessons = enrollment.getCourse().getModules().stream()
                .mapToLong(m -> lessonRepository.findByModuleIdOrderByOrderIndexAsc(m.getId()).size())
                .sum();
        long completedLessons = lessonProgressRepository.countCompletedLessonsForCourse(userId, courseId);

        if (totalLessons > 0 && completedLessons >= totalLessons) {
            enrollment.setStatus(Enrollment.EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(Instant.now());
            enrollmentRepository.save(enrollment);
            // Certificate generation hooks in here in a later phase.
        }
    }
}
