package dev.funbuild.vote;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

  Optional<Vote> findByProjectIdAndVoterId(UUID projectId, UUID voterId);

  boolean existsByProjectIdAndVoterId(UUID projectId, UUID voterId);

  long countByProjectId(UUID projectId);

  /** All project ids the given voter has voted for, out of the candidate ids — for list views. */
  @Query("SELECT v.project.id FROM Vote v WHERE v.voter.id = :voterId AND v.project.id IN :projectIds")
  Set<UUID> findVotedProjectIds(@Param("voterId") UUID voterId, @Param("projectIds") Collection<UUID> projectIds);
}
