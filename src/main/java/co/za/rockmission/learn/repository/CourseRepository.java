package co.za.rockmission.learn.repository;

import co.za.rockmission.learn.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    @EntityGraph(attributePaths = "createdBy")
    Optional<Course> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "createdBy")
    List<Course> findByStatus(Course.CourseStatus status);

    @EntityGraph(attributePaths = "createdBy")
    List<Course> findByCreatedById(Long educatorId);

    @EntityGraph(attributePaths = "createdBy")
    Optional<Course> findById(Long id);

    boolean existsByIdAndCreatedById(Long id, Long createdById);
}
