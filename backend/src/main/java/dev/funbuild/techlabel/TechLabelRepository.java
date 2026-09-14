package dev.funbuild.techlabel;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechLabelRepository extends JpaRepository<TechLabel, Long> {

  Optional<TechLabel> findByNameIgnoreCase(String name);

  List<TechLabel> findAllByOrderByNameAsc();

  List<TechLabel> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
