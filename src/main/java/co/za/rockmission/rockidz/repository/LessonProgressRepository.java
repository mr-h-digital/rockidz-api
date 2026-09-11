package co.za.rockmission.rockidz.repository;

import co.za.rockmission.rockidz.model.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    List<LessonProgress> findByUserId(Long userId);
    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, Long lessonId);

    // Count of completed lessons for a user within a specific course, for dashboard %
    @Query("""
        SELECT COUNT(lp) FROM LessonProgress lp
        WHERE lp.user.id = :userId
          AND lp.completedAt IS NOT NULL
          AND lp.lesson.module.course.id = :courseId
        """)
    long countCompletedLessonsForCourse(Long userId, Long courseId);
}
