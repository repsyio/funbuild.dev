package dev.funbuild.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
  @Autowired private EntityManager entityManager;
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
  void adminCanPromoteAndDemoteUserWithExistingTokenAndOnlyRoleChanges() throws Exception {
    User admin = saveUser("admin-role-update@example.com", Role.ADMIN);
    User target =
        saveUser(
            "target-role-update@example.com",
            "Target Member",
            "https://example.com/target-avatar.png",
            Role.MEMBER);
    String adminAuthorization = bearer(admin);
    String targetAuthorization = bearer(target);

    mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, targetAuthorization))
        .andExpect(status().isForbidden());

    mvc.perform(
            put("/api/users/{id}", target.getId())
                .header(HttpHeaders.AUTHORIZATION, adminAuthorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"role\":\"ADMIN\",\"email\":\"changed@example.com\","
                        + "\"displayName\":\"Changed\",\"avatarUrl\":\"https://example.com/changed.png\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(target.getId().toString()))
        .andExpect(jsonPath("$.data.email").value(target.getEmail()))
        .andExpect(jsonPath("$.data.displayName").value(target.getDisplayName()))
        .andExpect(jsonPath("$.data.avatarUrl").value(target.getAvatarUrl()))
        .andExpect(jsonPath("$.data.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.authProvider").value("LOCAL"));

    entityManager.clear();
    User promoted = userRepository.findById(target.getId()).orElseThrow();
    assertThat(promoted.getRole()).isEqualTo(Role.ADMIN);
    assertThat(promoted.getEmail()).isEqualTo(target.getEmail());
    assertThat(promoted.getDisplayName()).isEqualTo(target.getDisplayName());
    assertThat(promoted.getAvatarUrl()).isEqualTo(target.getAvatarUrl());

    mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, targetAuthorization))
        .andExpect(status().isOk());

    mvc.perform(
            put("/api/users/{id}", target.getId())
                .header(HttpHeaders.AUTHORIZATION, adminAuthorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MEMBER\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("MEMBER"));

    entityManager.clear();
    User demoted = userRepository.findById(target.getId()).orElseThrow();
    assertThat(demoted.getRole()).isEqualTo(Role.MEMBER);
    assertThat(demoted.getEmail()).isEqualTo(target.getEmail());
    assertThat(demoted.getDisplayName()).isEqualTo(target.getDisplayName());
    assertThat(demoted.getAvatarUrl()).isEqualTo(target.getAvatarUrl());

    mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, targetAuthorization))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedRoleUpdateIsUnauthorized() throws Exception {
    mvc.perform(
            put("/api/users/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void memberCannotUpdateUserRole() throws Exception {
    User member = saveUser("member-role-update@example.com", Role.MEMBER);

    mvc.perform(
            put("/api/users/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unknownUserRoleUpdateReturnsNotFound() throws Exception {
    User admin = saveUser("admin-role-unknown@example.com", Role.ADMIN);

    mvc.perform(
            put("/api/users/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("User not found"));
  }

  @Test
  void malformedUserRoleUpdateReturnsNotFound() throws Exception {
    User admin = saveUser("admin-role-malformed@example.com", Role.ADMIN);

    mvc.perform(
            put("/api/users/not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @MethodSource("invalidRoleRequests")
  void invalidRoleRequestReturnsBadRequestAndPreservesRole(String request) throws Exception {
    User admin = saveUser("admin-role-invalid-%s@example.com".formatted(UUID.randomUUID()), Role.ADMIN);
    User target = saveUser("target-role-invalid-%s@example.com".formatted(UUID.randomUUID()), Role.MEMBER);

    mvc.perform(
            put("/api/users/{id}", target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest());

    entityManager.clear();
    assertThat(userRepository.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.MEMBER);
  }

  private static Stream<Arguments> invalidRoleRequests() {
    return Stream.of(Arguments.of("{}"), Arguments.of("{\"role\":null}"), Arguments.of("{\"role\":\"OWNER\"}"));
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
    return saveUser(email, role == Role.ADMIN ? "Admin" : "Member", null, role);
  }

  private User saveUser(String email, String displayName, String avatarUrl, Role role) {
    User saved =
        userRepository.saveAndFlush(
            new User(
                email,
                null,
                displayName,
                avatarUrl,
                role,
                AuthProvider.LOCAL,
                null));
    return userRepository.findById(saved.getId()).orElseThrow();
  }

  private String bearer(User user) {
    return "Bearer " + jwtService.generateToken(user);
  }
}
