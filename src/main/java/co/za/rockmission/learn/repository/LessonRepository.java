package co.za.rockmission.learn.repository;

import co.za.rockmission.learn.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByModuleIdOrderByOrderIndexAsc(Long moduleId);

    @Query("select count(l) from Lesson l where l.module.course.id = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);
}
