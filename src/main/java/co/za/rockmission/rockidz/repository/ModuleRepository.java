package co.za.rockmission.rockidz.repository;

import co.za.rockmission.rockidz.model.Module;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuleRepository extends JpaRepository<Module, Long> {
    List<Module> findByCourseIdOrderByOrderIndexAsc(Long courseId);
}
