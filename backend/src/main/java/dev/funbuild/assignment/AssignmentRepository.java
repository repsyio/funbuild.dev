package dev.funbuild.assignment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

  List<Assignment> findAllByOrderByStartAtDesc();

  Optional<Assignment> findBySlug(String slug);

  boolean existsBySlug(String slug);

  @Query(
      "SELECT a FROM Assignment a WHERE a.startAt <= :now AND a.endAt >= :now ORDER BY a.endAt ASC")
  List<Assignment> findActive(@Param("now") Instant now);
}
