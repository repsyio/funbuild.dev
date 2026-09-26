package dev.funbuild.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.funbuild.assignment.Assignment;
import dev.funbuild.assignment.AssignmentRepository;
import dev.funbuild.security.JwtService;
import dev.funbuild.techlabel.TechLabel;
import dev.funbuild.techlabel.TechLabelRepository;
import dev.funbuild.user.AuthProvider;
import dev.funbuild.user.Role;
import dev.funbuild.user.User;
import dev.funbuild.user.UserRepository;
import dev.funbuild.vote.Vote;
import dev.funbuild.vote.VoteRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectControllerIntegrationTest {

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
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void ownerDeletionReturnsNoContentAndRemovesOnlyProjectOwnedRows() throws Exception {
    Graph graph = saveGraph();
    long siblingJoinRows = projectTechLabelCount(graph.sibling());

    deleteProject(graph.project(), graph.owner())
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertProjectDeleted(graph.project());
    assertGraphSurvives(graph, siblingJoinRows);
  }

  @Test
  void adminCanDeleteAnotherMemberProject() throws Exception {
    Graph graph = saveGraph();

    deleteProject(graph.sibling(), graph.admin())
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertProjectDeleted(graph.sibling());
    assertThat(projectRepository.findById(graph.project().getId())).isPresent();
    assertThat(assignmentRepository.findById(graph.assignment().getId())).isPresent();
  }

  @Test
  void unauthenticatedDeletionReturnsUnauthorizedAndPreservesState() throws Exception {
    Graph graph = saveGraph();
    long joinRows = projectTechLabelCount(graph.project());
    long votes = voteRepository.countByProjectId(graph.project().getId());

    mvc.perform(delete("/api/projects/{id}", graph.project().getId()))
        .andExpect(status().isUnauthorized());

    assertProjectState(graph.project(), joinRows, votes);
  }

  @Test
  void differentMemberCannotDeleteProjectAndStateIsPreserved() throws Exception {
    Graph graph = saveGraph();
    long joinRows = projectTechLabelCount(graph.project());
    long votes = voteRepository.countByProjectId(graph.project().getId());

    deleteProject(graph.project(), graph.siblingOwner()).andExpect(status().isForbidden());

    assertProjectState(graph.project(), joinRows, votes);
  }

  @Test
  void unknownProjectIdReturnsProjectNotFound() throws Exception {
    User user = saveUser("unknown-project", Role.MEMBER);

    mvc.perform(delete("/api/projects/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("Project not found"));
  }

  @Test
  void malformedProjectIdReturnsResourceNotFound() throws Exception {
    User user = saveUser("malformed-project", Role.MEMBER);

    mvc.perform(delete("/api/projects/{id}", "not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("Resource not found"));
  }

  @Test
  void repeatedDeletionReturnsNoContentThenNotFoundWithoutDependentRows() throws Exception {
    Graph graph = saveGraph();

    deleteProject(graph.project(), graph.owner())
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    deleteProject(graph.project(), graph.owner())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("Project not found"));

    assertProjectDeleted(graph.project());
    assertThat(projectRepository.findById(graph.sibling().getId())).isPresent();
    assertThat(projectTechLabelCount(graph.sibling())).isEqualTo(2);
  }

  private Graph saveGraph() {
    User owner = saveUser("owner", Role.MEMBER);
    User siblingOwner = saveUser("sibling-owner", Role.MEMBER);
    User admin = saveUser("admin", Role.ADMIN);
    User voter = saveUser("voter", Role.MEMBER);
    Assignment assignment = saveAssignment(owner);
    TechLabel sharedLabel = saveLabel("shared");
    TechLabel siblingLabel = saveLabel("sibling-only");
    Project project = saveProject(assignment, owner, "target", sharedLabel);
    Project sibling = saveProject(assignment, siblingOwner, "sibling", sharedLabel, siblingLabel);
    saveVote(project, owner);
    saveVote(project, voter);
    saveVote(sibling, voter);
    return new Graph(owner, siblingOwner, admin, voter, assignment, project, sibling, sharedLabel, siblingLabel);
  }

  private User saveUser(String name, Role role) {
    return userRepository.saveAndFlush(
        new User(
            "%s-%s@example.com".formatted(name, UUID.randomUUID()),
            "password-hash",
            "%s User".formatted(name),
            null,
            role,
            AuthProvider.LOCAL,
            null));
  }

  private Assignment saveAssignment(User creator) {
    return assignmentRepository.saveAndFlush(
        new Assignment(
            "delete-project-%s".formatted(UUID.randomUUID()),
            "Delete project assignment",
            "Assignment used by project deletion integration tests.",
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600),
            creator));
  }

  private TechLabel saveLabel(String name) {
    return techLabelRepository.saveAndFlush(
        new TechLabel(
            "%s-%s".formatted(name, UUID.randomUUID().toString().substring(0, 8)),
            saveUser("label-creator", Role.MEMBER)));
  }

  private Project saveProject(Assignment assignment, User submitter, String name, TechLabel... labels) {
    Project project =
        projectRepository.saveAndFlush(
            new Project(
                assignment,
                submitter,
                "%s project".formatted(name),
                "Project used by project deletion integration tests.",
                "https://example.com/%s".formatted(name),
                "https://github.com/example/%s".formatted(name)));
    project.setTechLabels(new LinkedHashSet<>(Set.of(labels)));
    return projectRepository.saveAndFlush(project);
  }

  private void saveVote(Project project, User voter) {
    voteRepository.saveAndFlush(new Vote(project, voter));
  }

  private ResultActions deleteProject(Project project, User user) throws Exception {
    return mvc.perform(
        delete("/api/projects/{id}", project.getId()).header(HttpHeaders.AUTHORIZATION, bearer(user)));
  }

  private String bearer(User user) {
    return "Bearer " + jwtService.generateToken(user);
  }

  private long projectTechLabelCount(Project project) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM project_tech_labels WHERE project_id = ?", Long.class, project.getId());
  }

  private void assertProjectDeleted(Project project) {
    assertThat(projectRepository.findById(project.getId())).isEmpty();
    assertThat(voteRepository.countByProjectId(project.getId())).isZero();
    assertThat(projectTechLabelCount(project)).isZero();
  }

  private void assertProjectState(Project project, long expectedJoinRows, long expectedVotes) {
    assertThat(projectRepository.findById(project.getId())).isPresent();
    assertThat(voteRepository.countByProjectId(project.getId())).isEqualTo(expectedVotes);
    assertThat(projectTechLabelCount(project)).isEqualTo(expectedJoinRows);
  }

  private void assertGraphSurvives(Graph graph, long expectedSiblingJoinRows) {
    assertThat(assignmentRepository.findById(graph.assignment().getId())).isPresent();
    assertThat(userRepository.findAllById(
            Set.of(graph.owner().getId(), graph.siblingOwner().getId(), graph.admin().getId(), graph.voter().getId())))
        .hasSize(4);
    assertThat(techLabelRepository.findById(graph.sharedLabel().getId())).isPresent();
    assertThat(techLabelRepository.findById(graph.siblingLabel().getId())).isPresent();
    assertThat(projectRepository.findById(graph.sibling().getId())).isPresent();
    assertThat(voteRepository.countByProjectId(graph.sibling().getId())).isEqualTo(1);
    assertThat(projectTechLabelCount(graph.sibling())).isEqualTo(expectedSiblingJoinRows);
  }

  private record Graph(
      User owner,
      User siblingOwner,
      User admin,
      User voter,
      Assignment assignment,
      Project project,
      Project sibling,
      TechLabel sharedLabel,
      TechLabel siblingLabel) {}
}
