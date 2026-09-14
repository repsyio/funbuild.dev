package dev.funbuild.assignment;

import dev.funbuild.error.NotFoundException;
import dev.funbuild.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {

  private final AssignmentRepository repository;

  public AssignmentService(AssignmentRepository repository) {
    this.repository = repository;
  }

  /**
   * Maps to the response DTO inside the transaction: {@code createdBy} is a lazy association, and
   * with {@code spring.jpa.open-in-view: false} the Hibernate session is gone by the time the
   * controller would otherwise touch it.
   */
  @Transactional(readOnly = true)
  public List<AssignmentResponse> listAll() {
    return repository.findAllByOrderByStartAtDesc().stream().map(AssignmentResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<AssignmentResponse> listActive() {
    return repository.findActive(Instant.now()).stream().map(AssignmentResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public AssignmentResponse get(String slug) {
    return AssignmentResponse.from(find(slug));
  }

  @Transactional
  public AssignmentResponse create(AssignmentRequest req, User creator) {
    validateDates(req.startAt(), req.endAt());
    Assignment assignment =
        new Assignment(
            generateUniqueSlug(req.title()), req.title(), req.description(), req.startAt(), req.endAt(), creator);
    return AssignmentResponse.from(repository.save(assignment));
  }

  @Transactional
  public AssignmentResponse update(String slug, AssignmentRequest req) {
    validateDates(req.startAt(), req.endAt());
    Assignment assignment = find(slug);
    assignment.update(req.title(), req.description(), req.startAt(), req.endAt());
    return AssignmentResponse.from(assignment);
  }

  @Transactional
  public void delete(String slug) {
    repository.delete(find(slug));
  }

  private Assignment find(String slug) {
    return repository.findBySlug(slug).orElseThrow(() -> new NotFoundException("Assignment not found"));
  }

  private void validateDates(Instant startAt, Instant endAt) {
    if (!endAt.isAfter(startAt)) {
      throw new IllegalArgumentException("endAt must be after startAt");
    }
  }

  /** Slugifies the title and appends a numeric suffix on collision, e.g. {@code mini-racing-game-2}. */
  private String generateUniqueSlug(String title) {
    String base = slugify(title);
    String slug = base;
    int suffix = 2;
    while (repository.existsBySlug(slug)) {
      slug = base + "-" + suffix++;
    }
    return slug;
  }

  private static String slugify(String title) {
    String slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (slug.length() > 80) {
      slug = slug.substring(0, 80).replaceAll("-+$", "");
    }
    return slug.isEmpty() ? "assignment" : slug;
  }
}
