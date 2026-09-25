package dev.funbuild.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
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
  @Transactional
  void adminCanListUsersInNewestFirstOrderWithSafeSummaries() throws Exception {
    userRepository.deleteAll();
    userRepository.flush();

    User admin =
        saveUser(
            "admin-list@example.com",
            "admin-password-hash-sentinel",
            "List Admin",
            "https://example.com/admin-avatar.png",
            Role.ADMIN,
            AuthProvider.LOCAL,
            null,
            Instant.parse("2020-01-01T00:00:00Z"));
    User localMember =
        saveUser(
            "local-list@example.com",
            "local-password-hash-sentinel",
            "Local Member",
            "https://example.com/local-avatar.png",
            Role.MEMBER,
            AuthProvider.LOCAL,
            null,
            Instant.parse("2020-01-02T00:00:00Z"));
    User oauthMember =
        saveUser(
            "oauth-list@example.com",
            null,
            "OAuth Member",
            "https://example.com/oauth-avatar.png",
            Role.MEMBER,
            AuthProvider.GITHUB,
            "oauth-provider-id-sentinel",
            Instant.parse("2020-01-03T00:00:00Z"));

    mvc.perform(
            get("/api/users")
                .param("unknown", "ignored")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SUCCESS"))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[*].id").value(contains(
            oauthMember.getId().toString(), localMember.getId().toString(), admin.getId().toString())))
        .andExpect(jsonPath("$.data[*].email").value(contains(
            oauthMember.getEmail(), localMember.getEmail(), admin.getEmail())))
        .andExpect(jsonPath("$.data[*].displayName").value(contains(
            oauthMember.getDisplayName(), localMember.getDisplayName(), admin.getDisplayName())))
        .andExpect(jsonPath("$.data[*].avatarUrl").value(contains(
            oauthMember.getAvatarUrl(), localMember.getAvatarUrl(), admin.getAvatarUrl())))
        .andExpect(jsonPath("$.data[*].role").value(contains("MEMBER", "MEMBER", "ADMIN")))
        .andExpect(jsonPath("$.data[*].authProvider").value(contains("GITHUB", "LOCAL", "LOCAL")))
        .andExpect(jsonPath("$.data[0]").value(aMapWithSize(6)))
        .andExpect(jsonPath("$.data[0].id").value(oauthMember.getId().toString()))
        .andExpect(jsonPath("$.data[0].email").value(oauthMember.getEmail()))
        .andExpect(jsonPath("$.data[0].displayName").value(oauthMember.getDisplayName()))
        .andExpect(jsonPath("$.data[0].avatarUrl").value(oauthMember.getAvatarUrl()))
        .andExpect(jsonPath("$.data[0].role").value("MEMBER"))
        .andExpect(jsonPath("$.data[0].authProvider").value("GITHUB"))
        .andExpect(content().string(not(containsString("passwordHash"))))
        .andExpect(content().string(not(containsString("providerId"))))
        .andExpect(content().string(not(containsString("password-hash-sentinel"))))
        .andExpect(content().string(not(containsString("oauth-provider-id-sentinel"))));
  }

  @Test
  @Transactional
  void listReturnsEmptyResultWhenNoUsersExist() {
    userRepository.deleteAll();
    userRepository.flush();
    assertThat(userRepository.findAllByOrderByCreatedAtDesc()).isEmpty();
  }

  @Test
  void memberCannotListUsers() throws Exception {
    User member = saveUser("member-list@example.com", Role.MEMBER);

    mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(member)))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedAndInvalidBearerCannotListUsers() throws Exception {
    mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());

    mvc.perform(
            get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer definitely-not-a-valid-token"))
        .andExpect(status().isUnauthorized());
  }

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
    return saveUser(email, null, displayName, avatarUrl, role, AuthProvider.LOCAL, null, Instant.now());
  }

  private User saveUser(
      String email,
      String passwordHash,
      String displayName,
      String avatarUrl,
      Role role,
      AuthProvider authProvider,
      String providerId,
      Instant createdAt) {
    User user =
        new User(email, passwordHash, displayName, avatarUrl, role, authProvider, providerId);
    ReflectionTestUtils.setField(user, "createdAt", createdAt);
    User saved =
        userRepository.saveAndFlush(user);
    return userRepository.findById(saved.getId()).orElseThrow();
  }

  private String bearer(User user) {
    return "Bearer " + jwtService.generateToken(user);
  }
}
