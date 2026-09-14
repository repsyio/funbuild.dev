package dev.funbuild.project;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

  List<Project> findAllByOrderByVoteCountDescCreatedAtDesc();

  List<Project> findAllByOrderByVoteCountDescCreatedAtDesc(Pageable pageable);

  List<Project> findAllByAssignmentIdOrderByVoteCountDescCreatedAtDesc(Long assignmentId);
}
