package dev.funbuild.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.funbuild.assignment.Assignment;
import dev.funbuild.assignment.AssignmentRepository;
import dev.funbuild.project.Project;
import dev.funbuild.project.ProjectRepository;
import dev.funbuild.techlabel.TechLabel;
import dev.funbuild.techlabel.TechLabelRepository;
import dev.funbuild.vote.Vote;
import dev.funbuild.vote.VoteRepository;
import dev.funbuild.security.JwtService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private AssignmentRepository assignmentRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TechLabelRepository techLabelRepository;
  @Autowired private VoteRepository voteRepository;

  @Test
  void adminDeleteReturnsNoContentCascadesOwnedRecordsAndInvalidatesToken() throws Exception {
    User admin = saveUser("admin-delete@example.com", Role.ADMIN);
    User target = saveUser("target-delete@example.com", Role.MEMBER);
    Assignment assignment =
        assignmentRepository.saveAndFlush(
            new Assignment(
                "target-assignment",
                "Target assignment",
                "An assignment owned by the deleted user.",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                target));
    TechLabel label = techLabelRepository.saveAndFlush(new TechLabel("Target label", target));
    Project project =
        new Project(
            assignment,
            target,
            "Target project",
            "A project owned by the deleted user.",
            "https://example.com/project",
            null);
    project.setTechLabels(Set.of(label));
    projectRepository.saveAndFlush(project);
    Vote vote = voteRepository.saveAndFlush(new Vote(project, target));
    String targetToken = bearer(target);

    mvc.perform(
            delete("/api/users/{id}", target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    userRepository.flush();
    assertThat(userRepository.findById(target.getId())).isEmpty();
    assertThat(assignmentRepository.findById(assignment.getId())).isEmpty();
    assertThat(projectRepository.findById(project.getId())).isEmpty();
    assertThat(techLabelRepository.findById(label.getId())).isEmpty();
    assertThat(voteRepository.findById(vote.getId())).isEmpty();

    mvc.perform(
            get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unauthenticatedDeleteIsUnauthorized() throws Exception {
    mvc.perform(delete("/api/users/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
  }

  @Test
  void memberCannotDeleteUser() throws Exception {
    User member = saveUser("member-delete@example.com", Role.MEMBER);

    mvc.perform(
            delete("/api/users/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(member)))
        .andExpect(status().isForbidden());
  }

  @Test
  void unknownUserReturnsNotFound() throws Exception {
    User admin = saveUser("admin-unknown@example.com", Role.ADMIN);

    mvc.perform(
            delete("/api/users/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("User not found"));
  }

  @Test
  void malformedUserIdReturnsNotFound() throws Exception {
    User admin = saveUser("admin-malformed@example.com", Role.ADMIN);

    mvc.perform(
            delete("/api/users/not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isNotFound());
  }

  @Test
  void repeatedDeletionReturnsNotFound() throws Exception {
    User admin = saveUser("admin-repeat@example.com", Role.ADMIN);
    User target = saveUser("target-repeat@example.com", Role.MEMBER);
    String authorization = bearer(admin);

    mvc.perform(delete("/api/users/{id}", target.getId()).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isNoContent());
    userRepository.flush();

    mvc.perform(delete("/api/users/{id}", target.getId()).header(HttpHeaders.AUTHORIZATION, authorization))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("User not found"));
  }

  private User saveUser(String email, Role role) {
    User saved =
        userRepository.saveAndFlush(
            new User(
                email,
                null,
                role == Role.ADMIN ? "Admin" : "Member",
                null,
                role,
                AuthProvider.LOCAL,
                null));
    return userRepository.findById(saved.getId()).orElseThrow();
  }

  private String bearer(User user) {
    return "Bearer " + jwtService.generateToken(user);
  }
}
