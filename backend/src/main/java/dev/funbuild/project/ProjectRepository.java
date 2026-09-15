package dev.funbuild.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

  List<Project> findAllByOrderByVoteCountDescCreatedAtDesc();

  List<Project> findAllByOrderByVoteCountDescCreatedAtDesc(Pageable pageable);

  List<Project> findAllByAssignmentIdOrderByVoteCountDescCreatedAtDesc(UUID assignmentId);
}
