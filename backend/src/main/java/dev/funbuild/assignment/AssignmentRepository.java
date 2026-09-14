package dev.funbuild.assignment;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

  List<Assignment> findAllByOrderByStartAtDesc();

  @Query(
      "SELECT a FROM Assignment a WHERE a.startAt <= :now AND a.endAt >= :now ORDER BY a.endAt ASC")
  List<Assignment> findActive(@Param("now") Instant now);
}
