package dev.funbuild.project;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Project p WHERE p.id = :id")
  Optional<Project> findByIdForUpdate(@Param("id") UUID id);

  List<Project> findAllByOrderByVoteCountDescCreatedAtDesc();

  List<Project> findAllByOrderByVoteCountDescCreatedAtDesc(Pageable pageable);

  List<Project> findAllByAssignmentIdOrderByVoteCountDescCreatedAtDesc(UUID assignmentId);
}
