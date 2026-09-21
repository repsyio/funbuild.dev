package dev.funbuild.techlabel;

import dev.funbuild.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import io.repsy.core.uuidv7.UuidV7;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tech_labels")
public class TechLabel {

  @Id
  @UuidV7
  private UUID id;

  @Column(nullable = false, length = 40)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TechLabel() {}

  public TechLabel(String name, User createdBy) {
    this.name = name;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
