package co.za.rockmission.learn.repository;

import co.za.rockmission.learn.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Course> findByStatus(Course.CourseStatus status);
    List<Course> findByCreatedById(Long educatorId);
}
