package dev.funbuild.vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.funbuild.assignment.Assignment;
import dev.funbuild.assignment.AssignmentRepository;
import dev.funbuild.project.Project;
import dev.funbuild.project.ProjectRepository;
import dev.funbuild.security.JwtService;
import dev.funbuild.user.AuthProvider;
import dev.funbuild.user.Role;
import dev.funbuild.user.User;
import dev.funbuild.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VoteControllerIntegrationTest {

  private static final int CONCURRENT_TOGGLE_COUNT = 8;
  private static final long REQUEST_TIMEOUT_SECONDS = 30;

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private VoteRepository voteRepository;

  @Test
  void authenticatedUserCanToggleVoteOnAndOff() throws Exception {
    User voter = saveUser("toggle@example.com");
    Project project = saveProject(voter);
    String authorization = bearer(voter);

    mvc.perform(post("/api/projects/{id}/vote", project.getId()).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.voteCount").value(1))
        .andExpect(jsonPath("$.data.votedByMe").value(true));

    mvc.perform(post("/api/projects/{id}/vote", project.getId()).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.voteCount").value(0))
        .andExpect(jsonPath("$.data.votedByMe").value(false));
  }

  @Test
  void missingAuthenticationIsRejected() throws Exception {
    Project project = saveProject(saveUser("missing-auth@example.com"));

    mvc.perform(post("/api/projects/{id}/vote", project.getId())).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownProjectIsNotFound() throws Exception {
    User voter = saveUser("unknown-project@example.com");

    mvc.perform(
            post("/api/projects/{id}/vote", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(voter)))
        .andExpect(status().isNotFound());
  }

  @Test
  void malformedProjectIdIsNotFound() throws Exception {
    User voter = saveUser("malformed-project@example.com");

    mvc.perform(
            post("/api/projects/{id}/vote", "not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, bearer(voter)))
        .andExpect(status().isNotFound());
  }

  @Test
  void votesAreIsolatedByUser() throws Exception {
    User firstVoter = saveUser("first-voter@example.com");
    User secondVoter = saveUser("second-voter@example.com");
    Project project = saveProject(firstVoter);

    mvc.perform(
            post("/api/projects/{id}/vote", project.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(firstVoter)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.voteCount").value(1))
        .andExpect(jsonPath("$.data.votedByMe").value(true));

    mvc.perform(
            post("/api/projects/{id}/vote", project.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(secondVoter)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.voteCount").value(2))
        .andExpect(jsonPath("$.data.votedByMe").value(true));

    mvc.perform(
            post("/api/projects/{id}/vote", project.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(firstVoter)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.voteCount").value(1))
        .andExpect(jsonPath("$.data.votedByMe").value(false));

    assertThat(voteRepository.countByProjectId(project.getId())).isEqualTo(1);
    assertThat(voteRepository.findByProjectIdAndVoterId(project.getId(), secondVoter.getId())).isPresent();
  }

  @Test
  void concurrentTogglesBySameUserAreSerializedAndReachExpectedParity()
      throws Exception {
    User voter = saveUser("concurrent-voter@example.com");
    Project project = saveProject(voter);
    String authorization = bearer(voter);
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TOGGLE_COUNT);
    List<Future<MvcResult>> responses = new ArrayList<>();

    try {
      for (int i = 0; i < CONCURRENT_TOGGLE_COUNT; i++) {
        responses.add(
            executor.submit(
                () ->
                    mvc.perform(
                            post("/api/projects/{id}/vote", project.getId())
                                .header(HttpHeaders.AUTHORIZATION, authorization))
                        .andReturn()));
      }

      for (Future<MvcResult> response : responses) {
        MvcResult result = getResponse(response);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Number voteCount = JsonPath.read(result.getResponse().getContentAsString(), "$.data.voteCount");
        Boolean votedByMe = JsonPath.read(result.getResponse().getContentAsString(), "$.data.votedByMe");
        assertThat(voteCount.longValue()).isIn(0L, 1L);
        assertThat(votedByMe).isEqualTo(voteCount.longValue() == 1L);
      }
    } finally {
      executor.shutdownNow();
    }

    long expectedVoteCount = CONCURRENT_TOGGLE_COUNT % 2;
    assertThat(voteRepository.countByProjectId(project.getId())).isEqualTo(expectedVoteCount);
    assertThat(voteRepository.findByProjectIdAndVoterId(project.getId(), voter.getId()).isPresent())
        .isEqualTo(expectedVoteCount == 1);
  }

  private MvcResult getResponse(Future<MvcResult> response) throws Exception {
    try {
      return response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException ex) {
      throw new AssertionError("Concurrent vote request failed", ex.getCause());
    }
  }

  private User saveUser(String emailPrefix) {
    return userRepository.saveAndFlush(
        new User(
            emailPrefix.replace("@", "-%s@".formatted(UUID.randomUUID())),
            "password-hash",
            "Vote Tester",
            null,
            Role.MEMBER,
            AuthProvider.LOCAL,
            null));
  }

  private Project saveProject(User submitter) {
    Assignment assignment =
        assignmentRepository.saveAndFlush(
            new Assignment(
                "vote-assignment-%s".formatted(UUID.randomUUID()),
                "Vote assignment",
                "Assignment used by vote integration tests.",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                submitter));
    return projectRepository.saveAndFlush(
        new Project(
            assignment,
            submitter,
            "Vote project",
            "Project used by vote integration tests.",
            "https://example.com/vote-project",
            null));
  }

  private String bearer(User user) {
    return "Bearer " + jwtService.generateToken(user);
  }
}
