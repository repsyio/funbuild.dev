package dev.funbuild.techlabel;

import dev.funbuild.user.User;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechLabelService {

  private static final int MAX_NAME_LENGTH = 40;
  private static final int MAX_LABELS_PER_PROJECT = 10;

  private final TechLabelRepository repository;

  public TechLabelService(TechLabelRepository repository) {
    this.repository = repository;
  }

  public List<TechLabel> search(String query) {
    if (query == null || query.isBlank()) {
      return repository.findAllByOrderByNameAsc();
    }
    return repository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
  }

  @Transactional
  public TechLabel findOrCreate(String rawName, User creator) {
    String name = rawName.trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Tech label name cannot be blank");
    }
    if (name.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("Tech label name is too long");
    }
    return repository.findByNameIgnoreCase(name).orElseGet(() -> repository.save(new TechLabel(name, creator)));
  }

  @Transactional
  public Set<TechLabel> findOrCreateAll(List<String> names, User creator) {
    if (names == null) {
      return Set.of();
    }
    if (names.size() > MAX_LABELS_PER_PROJECT) {
      throw new IllegalArgumentException("At most " + MAX_LABELS_PER_PROJECT + " tech labels per project");
    }
    LinkedHashSet<TechLabel> result = new LinkedHashSet<>();
    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      result.add(findOrCreate(name, creator));
    }
    return result;
  }
}
