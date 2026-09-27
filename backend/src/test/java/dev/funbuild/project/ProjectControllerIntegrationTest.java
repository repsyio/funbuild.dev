package dev.funbuild.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.funbuild.assignment.Assignment;
import dev.funbuild.assignment.AssignmentRepository;
import dev.funbuild.assignment.AssignmentStatus;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

  @Test
  void ownerCanUpdateProjectFieldsAndReplaceLabels() throws Exception {
    User owner = saveUser("update-owner", Role.MEMBER);
    Assignment assignment = saveAssignment(owner, AssignmentStatus.ACTIVE);
    String newLabelName = "New Tool-%s".formatted(UUID.randomUUID().toString().substring(0, 8));
    TechLabel existingLabel = saveLabel("React");
    String existingLabelName = existingLabel.getName();
    TechLabel removedLabel = saveLabel("Removed");
    Project project = saveProject(assignment, owner, "before-update", existingLabel, removedLabel);
    long labelCountBefore = techLabelRepository.count();

    updateProject(
            project,
            owner,
            projectRequestJson(
                assignment,
                "Updated project",
                "A changed description.",
                "https://updated.example.com",
                "https://github.com/example/updated",
                List.of("  %s  ".formatted(existingLabelName.toLowerCase()), newLabelName)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(project.getId().toString()))
        .andExpect(jsonPath("$.data.title").value("Updated project"))
        .andExpect(jsonPath("$.data.description").value("A changed description."))
        .andExpect(jsonPath("$.data.showcaseUrl").value("https://updated.example.com"))
        .andExpect(jsonPath("$.data.gitRepoUrl").value("https://github.com/example/updated"))
        .andExpect(jsonPath("$.data.techLabels[*].name").value(containsInAnyOrder(existingLabelName, newLabelName)));

    ProjectState state = projectState(project);
    assertThat(state.title()).isEqualTo("Updated project");
    assertThat(state.description()).isEqualTo("A changed description.");
    assertThat(state.showcaseUrl()).isEqualTo("https://updated.example.com");
    assertThat(state.gitRepoUrl()).isEqualTo("https://github.com/example/updated");
    assertThat(state.labels()).containsExactlyInAnyOrder(existingLabelName, newLabelName);
    assertThat(state.labels()).doesNotContain(removedLabel.getName());
    assertThat(techLabelRepository.findByNameIgnoreCase(existingLabelName)).isPresent();
    assertThat(techLabelRepository.findByNameIgnoreCase(newLabelName)).isPresent();
    assertThat(techLabelRepository.findById(removedLabel.getId())).isPresent();
    assertThat(techLabelRepository.count()).isEqualTo(labelCountBefore + 1);
  }

  @Test
  void adminCanUpdateAnotherMembersProjectWithoutChangingOwnership() throws Exception {
    User owner = saveUser("admin-update-owner", Role.MEMBER);
    User admin = saveUser("project-admin", Role.ADMIN);
    Assignment assignment = saveAssignment(owner, AssignmentStatus.ACTIVE);
    Project project = saveProject(assignment, owner, "admin-target");

    updateProject(
            project,
            admin,
            projectRequestJson(
                assignment,
                "Admin updated project",
                "Updated by an administrator.",
                "https://admin.example.com",
                "https://github.com/example/admin",
                List.of("Admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.submitter.id").value(owner.getId().toString()))
        .andExpect(jsonPath("$.data.title").value("Admin updated project"));

    ProjectState state = projectState(project);
    assertThat(state.submitterId()).isEqualTo(owner.getId());
    assertThat(state.title()).isEqualTo("Admin updated project");
  }

  @Test
  void unauthenticatedUpdateReturnsUnauthorizedAndPreservesProjectAndLabels() throws Exception {
    User owner = saveUser("unauthenticated-update-owner", Role.MEMBER);
    Assignment assignment = saveAssignment(owner, AssignmentStatus.ACTIVE);
    Project project = saveProject(assignment, owner, "unauthenticated-target", saveLabel("Keep"));
    ProjectState before = projectState(project);

    mvc.perform(
            put("/api/projects/{id}", project.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectRequestJson(assignment, "Should not update", "changed", "https://new.example.com", null, List.of())))
        .andExpect(status().isUnauthorized());

    assertThat(projectState(project)).isEqualTo(before);
  }

  @Test
  void differentMemberCannotUpdateProjectAndStateIsPreserved() throws Exception {
    User owner = saveUser("forbidden-update-owner", Role.MEMBER);
    User otherMember = saveUser("different-update-member", Role.MEMBER);
    Assignment assignment = saveAssignment(owner, AssignmentStatus.ACTIVE);
    Project project = saveProject(assignment, owner, "forbidden-target", saveLabel("Keep"));
    ProjectState before = projectState(project);

    updateProject(
            project,
            otherMember,
            projectRequestJson(assignment, "Should not update", "changed", "https://new.example.com", null, List.of()))
        .andExpect(status().isForbidden());

    assertThat(projectState(project)).isEqualTo(before);
  }

  @Test
  void unknownProjectIdReturnsNotFoundAndDoesNotChangeRecords() throws Exception {
    User owner = saveUser("unknown-update-owner", Role.MEMBER);
    Assignment assignment = saveAssignment(owner, AssignmentStatus.ACTIVE);
    Project project = saveProject(assignment, owner, "unknown-target", saveLabel("Keep"));
    ProjectState before = projectState(project);
    long labelsBefore = techLabelRepository.count();
    UUID unknownId = UUID.randomUUID();

    updateProject(
            unknownId,
            owner,
            projectRequestJson(assignment, "Unknown", "changed", "https://unknown.example.com", null, List.of("New")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("Project not found"));

    assertThat(projectState(project)).isEqualTo(before);
    assertThat(techLabelRepository.count()).isEqualTo(labelsBefore);
    assertThat(projectRepository.findById(unknownId)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"UPCOMING", "EXPIRED"})
  void nonActiveAssignmentReturnsConflictAndPreservesState(String statusName) throws Exception {
    User owner = saveUser("inactive-update-owner", Role.MEMBER);
    Assignment assignment = saveAssignment(owner, AssignmentStatus.valueOf(statusName));
    Project project = saveProject(assignment, owner, "inactive-target", saveLabel("Keep"));
    ProjectState before = projectState(project);

    updateProject(
            project,
            owner,
            projectRequestJson(assignment, "Should not update", "changed", "https://new.example.com", null, List.of("New")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.text").value("This assignment is no longer accepting changes"));

    assertThat(projectState(project)).isEqualTo(before);
    assertThat(techLabelRepository.findByNameIgnoreCase("New")).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("invalidUpdateBodies")
  void invalidUpdateBodyReturnsBadRequestAndPreservesState(String body) throws Exception {
    User owner = saveUser("invalid-update-owner", Role.MEMBER);
    Assignment assignment = saveAssignment(owner, AssignmentStatus.ACTIVE);
    Project project = saveProject(assignment, owner, "invalid-target", saveLabel("Keep"));
    ProjectState before = projectState(project);

    mvc.perform(
            put("/api/projects/{id}", project.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    assertThat(projectState(project)).isEqualTo(before);
  }

  @Test
  void updateRollsBackProjectAndNewLabelWhenALaterLabelIsInvalid() throws Exception {
    User owner = saveUser("rollback-update-owner", Role.MEMBER);
    Assignment assignment = saveAssignment(owner, AssignmentStatus.ACTIVE);
    TechLabel oldLabel = saveLabel("Rollback old");
    Project project = saveProject(assignment, owner, "rollback-target", oldLabel);
    ProjectState before = projectState(project);
    long labelsBefore = techLabelRepository.count();
    String tooLongLabel = "x".repeat(41);

    updateProject(
            project,
            owner,
            projectRequestJson(
                assignment,
                "Should roll back",
                "This update must not persist.",
                "https://rollback.example.com",
                "https://github.com/example/rollback",
                List.of("Valid new label", tooLongLabel)))
        .andExpect(status().isBadRequest());

    assertThat(projectState(project)).isEqualTo(before);
    assertThat(techLabelRepository.count()).isEqualTo(labelsBefore);
    assertThat(techLabelRepository.findByNameIgnoreCase("Valid new label")).isEmpty();
    assertThat(techLabelRepository.findByNameIgnoreCase(tooLongLabel)).isEmpty();
  }

  private static Stream<String> invalidUpdateBodies() {
    String valid =
        """
        {
          "assignmentId": "%s",
          "title": "%s",
          "description": "Description",
          "showcaseUrl": "%s",
          "gitRepoUrl": "https://github.com/example/project",
          "techLabels": ["Keep"]
        }
        """;
    UUID assignmentId = UUID.randomUUID();
    return Stream.of(
        valid.formatted(assignmentId, " ", "https://example.com"),
        """
        {
          "assignmentId": "%s",
          "description": "Description",
          "showcaseUrl": "https://example.com",
          "techLabels": ["Keep"]
        }
        """.formatted(assignmentId),
        valid.formatted(assignmentId, "Valid title", "not-a-url"),
        "{");
  }

  private ResultActions updateProject(Project project, User user, String body) throws Exception {
    return updateProject(project.getId(), user, body);
  }

  private ResultActions updateProject(UUID projectId, User user, String body) throws Exception {
    return mvc.perform(
        put("/api/projects/{id}", projectId)
            .header(HttpHeaders.AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private String projectRequestJson(
      Assignment assignment,
      String title,
      String description,
      String showcaseUrl,
      String gitRepoUrl,
      List<String> labels) {
    String gitRepoField = gitRepoUrl == null ? "null" : "\"%s\"".formatted(gitRepoUrl);
    String labelsJson =
        labels.stream()
            .map(label -> "\"%s\"".formatted(label))
            .collect(java.util.stream.Collectors.joining(", "));
    return """
        {
          "assignmentId": "%s",
          "title": "%s",
          "description": "%s",
          "showcaseUrl": "%s",
          "gitRepoUrl": %s,
          "techLabels": [%s]
        }
        """.formatted(
        assignment.getId(), title, description, showcaseUrl, gitRepoField, labelsJson);
  }

  private ProjectState projectState(Project project) {
    Project current = projectRepository.findById(project.getId()).orElseThrow();
    return new ProjectState(
        current.getTitle(),
        current.getDescription(),
        current.getShowcaseUrl(),
        current.getGitRepoUrl(),
        current.getAssignment().getId(),
        current.getSubmitter().getId(),
        projectLabelNames(project.getId()));
  }

  private List<String> projectLabelNames(UUID projectId) {
    return jdbcTemplate.queryForList(
        """
        SELECT tl.name
        FROM project_tech_labels ptl
        JOIN tech_labels tl ON tl.id = ptl.tech_label_id
        WHERE ptl.project_id = ?
        ORDER BY tl.name
        """,
        String.class,
        projectId);
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
    return saveAssignment(creator, AssignmentStatus.ACTIVE);
  }

  private Assignment saveAssignment(User creator, AssignmentStatus status) {
    Instant startAt =
        status == AssignmentStatus.UPCOMING
            ? Instant.now().plusSeconds(3600)
            : Instant.now().minusSeconds(7200);
    Instant endAt =
        status == AssignmentStatus.UPCOMING
            ? Instant.now().plusSeconds(7200)
            : status == AssignmentStatus.EXPIRED
                ? Instant.now().minusSeconds(3600)
                : Instant.now().plusSeconds(3600);
    return assignmentRepository.saveAndFlush(
        new Assignment(
            "delete-project-%s".formatted(UUID.randomUUID()),
            "Delete project assignment",
            "Assignment used by project deletion integration tests.",
            startAt,
            endAt,
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

  private record ProjectState(
      String title,
      String description,
      String showcaseUrl,
      String gitRepoUrl,
      UUID assignmentId,
      UUID submitterId,
      List<String> labels) {}
}
