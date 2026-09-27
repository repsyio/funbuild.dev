package dev.funbuild.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.funbuild.user.AuthProvider;
import dev.funbuild.user.Role;
import dev.funbuild.user.User;
import dev.funbuild.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AssignmentControllerIntegrationTest {

  private static final Instant UPCOMING_START = Instant.parse("2026-10-10T00:00:00Z");
  private static final Instant UPCOMING_END = Instant.parse("2026-10-20T00:00:00Z");
  private static final Instant ACTIVE_LATE_START = Instant.parse("2026-09-01T00:00:00Z");
  private static final Instant ACTIVE_LATE_END = Instant.parse("2026-10-15T00:00:00Z");
  private static final Instant ACTIVE_EARLY_START = Instant.parse("2026-08-01T00:00:00Z");
  private static final Instant ACTIVE_EARLY_END = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant EXPIRED_START = Instant.parse("2026-07-01T00:00:00Z");
  private static final Instant EXPIRED_END = Instant.parse("2026-08-01T00:00:00Z");

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @Autowired private MockMvc mvc;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void clearDatabase() {
    assignmentRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }

  @Test
  void omittedStatusReturnsAllAssignmentsInStartDateOrderWithSafeResponseShape() throws Exception {
    Fixture fixture = seedAssignments();

    mvc.perform(get("/api/assignments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SUCCESS"))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(
            jsonPath("$.data[*].slug")
                .value(
                    contains(
                        fixture.upcoming().getSlug(),
                        fixture.activeLate().getSlug(),
                        fixture.activeEarly().getSlug(),
                        fixture.expired().getSlug())))
        .andExpect(jsonPath("$.data[*].status").value(contains("UPCOMING", "ACTIVE", "ACTIVE", "EXPIRED")))
        .andExpect(jsonPath("$.data[0]").value(aMapWithSize(9)))
        .andExpect(jsonPath("$.data[0].id").isString())
        .andExpect(jsonPath("$.data[0].slug").value(fixture.upcoming().getSlug()))
        .andExpect(jsonPath("$.data[0].title").value(fixture.upcoming().getTitle()))
        .andExpect(jsonPath("$.data[0].description").value(fixture.upcoming().getDescription()))
        .andExpect(jsonPath("$.data[0].startAt").value(UPCOMING_START.toString()))
        .andExpect(jsonPath("$.data[0].endAt").value(UPCOMING_END.toString()))
        .andExpect(jsonPath("$.data[0].createdAt").isString())
        .andExpect(jsonPath("$.data[0].createdBy").value(aMapWithSize(6)))
        .andExpect(jsonPath("$.data[0].createdBy.id").value(fixture.creator().getId().toString()))
        .andExpect(jsonPath("$.data[0].createdBy.email").value(fixture.creator().getEmail()))
        .andExpect(jsonPath("$.data[0].createdBy.displayName").value(fixture.creator().getDisplayName()))
        .andExpect(jsonPath("$.data[0].createdBy.avatarUrl").value(fixture.creator().getAvatarUrl()))
        .andExpect(jsonPath("$.data[0].createdBy.role").value("MEMBER"))
        .andExpect(jsonPath("$.data[0].createdBy.authProvider").value("LOCAL"))
        .andExpect(content().string(not(containsString("passwordHash"))))
        .andExpect(content().string(not(containsString("providerId"))))
        .andExpect(content().string(not(containsString("assignment-password-hash-sentinel"))))
        .andExpect(content().string(not(containsString("assignment-provider-id-sentinel"))));

    assertThat(assignmentRepository.count()).isEqualTo(4);
  }

  @Test
  void activeStatusReturnsOnlyActiveAssignmentsInEndDateOrder() throws Exception {
    Fixture fixture = seedAssignments();

    mvc.perform(get("/api/assignments").param("status", "active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SUCCESS"))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(
            jsonPath("$.data[*].slug")
                .value(contains(fixture.activeEarly().getSlug(), fixture.activeLate().getSlug())))
        .andExpect(jsonPath("$.data[*].status").value(contains("ACTIVE", "ACTIVE")))
        .andExpect(jsonPath("$.data[0].endAt").value(ACTIVE_EARLY_END.toString()))
        .andExpect(jsonPath("$.data[1].endAt").value(ACTIVE_LATE_END.toString()));

    assertThat(assignmentRepository.count()).isEqualTo(4);
  }

  @ParameterizedTest
  @ValueSource(strings = {"active", "ACTIVE", "AcTiVe"})
  void activeStatusFilterIsCaseInsensitive(String statusValue) throws Exception {
    Fixture fixture = seedAssignments();

    mvc.perform(get("/api/assignments").param("status", statusValue))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].slug").value(contains(fixture.activeEarly().getSlug(), fixture.activeLate().getSlug())))
        .andExpect(jsonPath("$.data[*].status").value(contains("ACTIVE", "ACTIVE")));
  }

  @Test
  void unsupportedStatusFallsBackToAllAssignmentsWithoutMutation() throws Exception {
    Fixture fixture = seedAssignments();
    List<AssignmentState> before = assignmentStates();

    mvc.perform(get("/api/assignments").param("status", "archived"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SUCCESS"))
        .andExpect(
            jsonPath("$.data[*].slug")
                .value(
                    contains(
                        fixture.upcoming().getSlug(),
                        fixture.activeLate().getSlug(),
                        fixture.activeEarly().getSlug(),
                        fixture.expired().getSlug())));

    assertThat(assignmentStates()).containsExactlyElementsOf(before);
  }

  @Test
  void emptyDatabaseReturnsPublicSuccessEmptyArray() throws Exception {
    mvc.perform(get("/api/assignments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SUCCESS"))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());

    assertThat(assignmentRepository.count()).isZero();
  }

  private Fixture seedAssignments() {
    User creator =
        userRepository.saveAndFlush(
            new User(
                "assignment-creator-%s@example.com".formatted(UUID.randomUUID()),
                "assignment-password-hash-sentinel",
                "Assignment Creator",
                "https://example.com/assignment-creator.png",
                Role.MEMBER,
                AuthProvider.LOCAL,
                "assignment-provider-id-sentinel"));

    Assignment upcoming =
        saveAssignment("upcoming-assignment", "Upcoming assignment", UPCOMING_START, UPCOMING_END, creator);
    Assignment activeLate =
        saveAssignment("active-late-assignment", "Active late assignment", ACTIVE_LATE_START, ACTIVE_LATE_END, creator);
    Assignment activeEarly =
        saveAssignment(
            "active-early-assignment", "Active early assignment", ACTIVE_EARLY_START, ACTIVE_EARLY_END, creator);
    Assignment expired =
        saveAssignment("expired-assignment", "Expired assignment", EXPIRED_START, EXPIRED_END, creator);
    return new Fixture(creator, upcoming, activeLate, activeEarly, expired);
  }

  private Assignment saveAssignment(
      String slug, String title, Instant startAt, Instant endAt, User creator) {
    return assignmentRepository.saveAndFlush(
        new Assignment(
            slug,
            title,
            "Assignment description for " + title,
            startAt,
            endAt,
            creator));
  }

  private List<AssignmentState> assignmentStates() {
    return assignmentRepository.findAllByOrderByStartAtDesc().stream()
        .map(
            assignment ->
                new AssignmentState(
                    assignment.getId(),
                    assignment.getSlug(),
                    assignment.getTitle(),
                    assignment.getDescription(),
                    assignment.getStartAt(),
                    assignment.getEndAt(),
                    assignment.getCreatedBy().getId()))
        .toList();
  }

  private record Fixture(
      User creator,
      Assignment upcoming,
      Assignment activeLate,
      Assignment activeEarly,
      Assignment expired) {}

  private record AssignmentState(
      UUID id,
      String slug,
      String title,
      String description,
      Instant startAt,
      Instant endAt,
      UUID creatorId) {}
}
