package dev.funbuild.vote;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<Vote, Long> {

  Optional<Vote> findByProjectIdAndVoterId(Long projectId, Long voterId);

  boolean existsByProjectIdAndVoterId(Long projectId, Long voterId);

  long countByProjectId(Long projectId);

  /** All project ids the given voter has voted for, out of the candidate ids — for list views. */
  @Query("SELECT v.project.id FROM Vote v WHERE v.voter.id = :voterId AND v.project.id IN :projectIds")
  Set<Long> findVotedProjectIds(@Param("voterId") Long voterId, @Param("projectIds") Collection<Long> projectIds);
}
