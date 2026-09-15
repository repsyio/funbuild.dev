package dev.funbuild.techlabel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechLabelRepository extends JpaRepository<TechLabel, UUID> {

  Optional<TechLabel> findByNameIgnoreCase(String name);

  List<TechLabel> findAllByOrderByNameAsc();

  List<TechLabel> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
