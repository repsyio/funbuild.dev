package dev.funbuild.assignment;

import dev.funbuild.error.NotFoundException;
import dev.funbuild.user.User;
import java.time.Instant;
import java.util.List;
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
  public AssignmentResponse get(Long id) {
    return AssignmentResponse.from(find(id));
  }

  @Transactional
  public AssignmentResponse create(AssignmentRequest req, User creator) {
    validateDates(req.startAt(), req.endAt());
    Assignment assignment =
        new Assignment(req.title(), req.description(), req.startAt(), req.endAt(), creator);
    return AssignmentResponse.from(repository.save(assignment));
  }

  @Transactional
  public AssignmentResponse update(Long id, AssignmentRequest req) {
    validateDates(req.startAt(), req.endAt());
    Assignment assignment = find(id);
    assignment.update(req.title(), req.description(), req.startAt(), req.endAt());
    return AssignmentResponse.from(assignment);
  }

  @Transactional
  public void delete(Long id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException("Assignment not found");
    }
    repository.deleteById(id);
  }

  private Assignment find(Long id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("Assignment not found"));
  }

  private void validateDates(Instant startAt, Instant endAt) {
    if (!endAt.isAfter(startAt)) {
      throw new IllegalArgumentException("endAt must be after startAt");
    }
  }
}
