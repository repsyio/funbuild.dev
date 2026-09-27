package dev.funbuild.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectControllerIntegrationTest {

  private static final UUID INVALID_BODY_ASSIGNMENT_ID = UUID.randomUUID();

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
  void defaultTopRequestReturnsTenRankedProjectsAndViewerVoteFlags() throws Exception {
    TopProjectsFixture fixture = saveTopProjectsFixture();

    MvcResult result =
        mvc.perform(
                get("/api/projects/top")
                    .header(HttpHeaders.AUTHORIZATION, bearer(fixture.viewer())))
            .andExpect(status().isOk())
            .andReturn();

    String response = result.getResponse().getContentAsString();
    List<String> titles = JsonPath.read(response, "$.data[*].title");
    List<Number> voteCounts = JsonPath.read(response, "$.data[*].voteCount");
    List<Boolean> votedByMe = JsonPath.read(response, "$.data[*].votedByMe");

    assertThat(titles)
        .containsExactlyElementsOf(fixture.projects().subList(0, 10).stream().map(Project::getTitle).toList());
    assertThat(voteCounts).hasSize(10);
    for (int index = 0; index < voteCounts.size(); index++) {
      assertThat(voteCounts.get(index).longValue()).isEqualTo(11L - index);
    }
    assertThat(votedByMe)
        .containsExactly(true, false, false, true, false, false, false, false, false, false);
  }

  @Test
  void oversizedTopLimitIsCappedAtFifty() throws Exception {
    clearDatabase();
    User submitter = saveUser("top-limit-submitter", Role.MEMBER);
    Assignment assignment = saveAssignment(submitter);
    for (int index = 1; index <= 51; index++) {
      saveProject(assignment, submitter, "top-limit-%02d".formatted(index));
    }

    MvcResult cappedResult =
        mvc.perform(get("/api/projects/top").param("limit", "50"))
            .andExpect(status().isOk())
            .andReturn();
    MvcResult oversizedResult =
        mvc.perform(get("/api/projects/top").param("limit", "500"))
            .andExpect(status().isOk())
            .andReturn();

    List<?> cappedData = JsonPath.read(cappedResult.getResponse().getContentAsString(), "$.data");
    List<?> oversizedData = JsonPath.read(oversizedResult.getResponse().getContentAsString(), "$.data");
    assertThat(cappedData).hasSize(50);
    assertThat(oversizedData).isEqualTo(cappedData);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void nonPositiveTopLimitReturnsBadRequest(int limit) throws Exception {
    mvc.perform(get("/api/projects/top").param("limit", String.valueOf(limit)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void malformedTopLimitDocumentsCurrentResourceNotFoundResponse() throws Exception {
    mvc.perform(get("/api/projects/top").param("limit", "not-an-integer"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("Resource not found"));
  }

  @Test
  void emptyDatabaseReturnsPublicEmptyTopProjectData() throws Exception {
    clearDatabase();

    mvc.perform(get("/api/projects/top"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void authenticatedMemberCanSubmitProjectAndPersistsExpectedGraph(boolean omitGitRepoUrl) throws Exception {
    User submitter = saveUser("create-submitter", Role.MEMBER);
    Assignment assignment = saveAssignment(submitter, AssignmentStatus.ACTIVE);
    TechLabel existingLabel = saveLabel("React");
    String existingLabelName = existingLabel.getName();
    String newLabelName = "Astro-%s".formatted(UUID.randomUUID().toString().substring(0, 8));
    long projectsBefore = projectRepository.count();
    long labelsBefore = techLabelRepository.count();
    long joinsBefore = projectTechLabelCount();
    long votesBefore = voteRepository.count();

    String body =
        projectRequestJson(
            assignment,
            "A new project",
            "A project submitted through the API.",
            "https://showcase.example.com/project",
            omitGitRepoUrl ? null : "https://github.com/example/project",
            List.of("  %s  ".formatted(existingLabelName.toLowerCase()), "  %s  ".formatted(newLabelName)))
            .replace(
                omitGitRepoUrl
                    ? "          \"gitRepoUrl\": null,\n"
                    : "          \"gitRepoUrl\": \"https://github.com/example/project\",\n",
                omitGitRepoUrl ? "" : "          \"gitRepoUrl\": \"https://github.com/example/project\",\n");

    ResultActions response = submitProject(submitter, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SUCCESS"))
        .andExpect(jsonPath("$.data.id").isNotEmpty())
        .andExpect(jsonPath("$.data.assignmentId").value(assignment.getId().toString()))
        .andExpect(jsonPath("$.data.assignmentSlug").value(assignment.getSlug()))
        .andExpect(jsonPath("$.data.submitter.id").value(submitter.getId().toString()))
        .andExpect(jsonPath("$.data.title").value("A new project"))
        .andExpect(jsonPath("$.data.description").value("A project submitted through the API."))
        .andExpect(jsonPath("$.data.showcaseUrl").value("https://showcase.example.com/project"))
        .andExpect(jsonPath("$.data.techLabels[*].name").value(containsInAnyOrder(existingLabelName, newLabelName)))
        .andExpect(jsonPath("$.data.voteCount").value(0))
        .andExpect(jsonPath("$.data.votedByMe").value(false));

    if (omitGitRepoUrl) {
      response.andExpect(jsonPath("$.data.gitRepoUrl").doesNotExist());
    } else {
      response.andExpect(jsonPath("$.data.gitRepoUrl").value("https://github.com/example/project"));
    }
    var result = response.andReturn();

    UUID projectId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"));
    Project saved = projectRepository.findById(projectId).orElseThrow();
    assertThat(saved.getAssignment().getId()).isEqualTo(assignment.getId());
    assertThat(saved.getSubmitter().getId()).isEqualTo(submitter.getId());
    assertThat(saved.getTitle()).isEqualTo("A new project");
    assertThat(saved.getDescription()).isEqualTo("A project submitted through the API.");
    assertThat(saved.getShowcaseUrl()).isEqualTo("https://showcase.example.com/project");
    assertThat(saved.getGitRepoUrl()).isEqualTo(omitGitRepoUrl ? null : "https://github.com/example/project");
    assertThat(projectLabelNames(projectId)).containsExactlyInAnyOrder(existingLabelName, newLabelName);
    assertThat(projectTechLabelIds(projectId)).containsExactlyInAnyOrder(existingLabel.getId(),
        techLabelRepository.findByNameIgnoreCase(newLabelName).orElseThrow().getId());
    assertThat(voteRepository.countByProjectId(projectId)).isZero();
    assertThat(projectRepository.count()).isEqualTo(projectsBefore + 1);
    assertThat(techLabelRepository.count()).isEqualTo(labelsBefore + 1);
    assertThat(projectTechLabelCount()).isEqualTo(joinsBefore + 2);
    assertThat(voteRepository.count()).isEqualTo(votesBefore);
  }

  @Test
  void unauthenticatedSubmissionReturnsUnauthorizedAndPreservesState() throws Exception {
    User creator = saveUser("unauthenticated-create", Role.MEMBER);
    Assignment assignment = saveAssignment(creator, AssignmentStatus.ACTIVE);
    StateCounts before = stateCounts();

    mvc.perform(
            post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectRequestJson(assignment, "Rejected", "No write", "https://example.com", null,
                    List.of("No write"))))
        .andExpect(status().isUnauthorized());

    assertThat(stateCounts()).isEqualTo(before);
  }

  @Test
  void unknownAssignmentReturnsNotFoundAndPreservesState() throws Exception {
    User submitter = saveUser("unknown-assignment", Role.MEMBER);
    Assignment assignment = saveAssignment(submitter, AssignmentStatus.ACTIVE);
    StateCounts before = stateCounts();

    submitProject(
            submitter,
            projectRequestJson(
                UUID.randomUUID(),
                "Unknown assignment",
                "No write",
                "https://example.com",
                null,
                List.of("No write")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.text").value("Assignment not found"));

    assertThat(stateCounts()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"UPCOMING", "EXPIRED"})
  void inactiveAssignmentReturnsConflictAndPreservesState(String statusName) throws Exception {
    User submitter = saveUser("inactive-create", Role.MEMBER);
    Assignment assignment = saveAssignment(submitter, AssignmentStatus.valueOf(statusName));
    StateCounts before = stateCounts();

    submitProject(
            submitter,
            projectRequestJson(
                assignment,
                "Inactive assignment",
                "No write",
                "https://example.com",
                null,
                List.of("No write")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.text").value("This assignment is no longer accepting submissions"));

    assertThat(stateCounts()).isEqualTo(before);
  }

  @ParameterizedTest
  @MethodSource("invalidCreateBodies")
  void invalidSubmissionBodyReturnsBadRequestAndPreservesState(String body) throws Exception {
    User submitter = saveUser("invalid-create", Role.MEMBER);
    Assignment assignment = saveAssignment(submitter, AssignmentStatus.ACTIVE);
    StateCounts before = stateCounts();

    submitProject(submitter, body.replace(INVALID_BODY_ASSIGNMENT_ID.toString(), assignment.getId().toString()))
        .andExpect(status().isBadRequest());

    assertThat(stateCounts()).isEqualTo(before);
    assertThat(projectRepository.findAllByAssignmentIdOrderByVoteCountDescCreatedAtDesc(assignment.getId())).isEmpty();
  }

  private static Stream<String> invalidCreateBodies() {
    String valid = validCreateRequestJson(INVALID_BODY_ASSIGNMENT_ID);
    String tooLongTitle = "x".repeat(201);
    String tooLongShowcaseUrl = "https://example.com/" + "x".repeat(500);
    String tooLongRepoUrl = "https://github.com/example/" + "x".repeat(500);
    String tooManyLabels =
        "[\"label-1\", \"label-2\", \"label-3\", \"label-4\", \"label-5\", \"label-6\", "
            + "\"label-7\", \"label-8\", \"label-9\", \"label-10\", \"label-11\"]";

    return Stream.of(
        """
        {
          "title": "Valid title",
          "description": "Description",
          "showcaseUrl": "https://example.com"
        }
        """,
        valid.replace(INVALID_BODY_ASSIGNMENT_ID.toString(), "not-a-uuid"),
        valid.replace("\"title\": \"Valid title\",", ""),
        valid.replace("\"title\": \"Valid title\"", "\"title\": \" \""),
        valid.replace("\"title\": \"Valid title\"", "\"title\": \"%s\"".formatted(tooLongTitle)),
        valid.replace("\"description\": \"Description\",", ""),
        valid.replace("\"description\": \"Description\"", "\"description\": \" \""),
        valid.replace("\"showcaseUrl\": \"https://example.com\",", ""),
        valid.replace("\"showcaseUrl\": \"https://example.com\"", "\"showcaseUrl\": \" \""),
        valid.replace("\"showcaseUrl\": \"https://example.com\"", "\"showcaseUrl\": \"not-a-url\""),
        valid.replace("\"showcaseUrl\": \"https://example.com\"", "\"showcaseUrl\": \"%s\"".formatted(tooLongShowcaseUrl)),
        valid.replace("\"gitRepoUrl\": null,", "\"gitRepoUrl\": \"not-a-url\",") ,
        valid.replace("\"gitRepoUrl\": null,", "\"gitRepoUrl\": \"%s\",".formatted(tooLongRepoUrl)),
        "{",
        valid.replace("\"techLabels\": []", "\"techLabels\": %s".formatted(tooManyLabels)),
        valid.replace("\"techLabels\": []", "\"techLabels\": [\"%s\"]".formatted("x".repeat(41))));
  }

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

  private String projectRequestJson(
      UUID assignmentId,
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
        """.formatted(assignmentId, title, description, showcaseUrl, gitRepoField, labelsJson);
  }

  private static String validCreateRequestJson(UUID assignmentId) {
    return """
        {
          "assignmentId": "%s",
          "title": "Valid title",
          "description": "Description",
          "showcaseUrl": "https://example.com",
          "gitRepoUrl": null,
          "techLabels": []
        }
        """.formatted(assignmentId);
  }

  private ResultActions submitProject(User user, String body) throws Exception {
    return mvc.perform(
        post("/api/projects")
            .header(HttpHeaders.AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private StateCounts stateCounts() {
    return new StateCounts(
        projectRepository.count(), techLabelRepository.count(), projectTechLabelCount(), voteRepository.count());
  }

  private TopProjectsFixture saveTopProjectsFixture() {
    clearDatabase();
    User submitter = saveUser("top-project-submitter", Role.MEMBER);
    Assignment assignment = saveAssignment(submitter);
    List<User> voters = new ArrayList<>();
    for (int index = 0; index < 11; index++) {
      voters.add(saveUser("top-project-voter-%02d".formatted(index), Role.MEMBER));
    }

    User viewer = voters.get(0);
    List<Project> projects = new ArrayList<>();
    for (int rank = 11; rank >= 1; rank--) {
      Project project = saveProject(assignment, submitter, "top-project-%02d".formatted(rank));
      projects.add(project);

      List<User> projectVoters = new ArrayList<>();
      if (rank == 11 || rank == 8) {
        projectVoters.add(viewer);
      }
      int nextVoter = 1;
      while (projectVoters.size() < rank) {
        projectVoters.add(voters.get(nextVoter++));
      }
      projectVoters.forEach(voter -> saveVote(project, voter));
    }
    return new TopProjectsFixture(viewer, projects);
  }

  private void clearDatabase() {
    jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
  }

  private long projectTechLabelCount() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM project_tech_labels", Long.class);
  }

  private List<UUID> projectTechLabelIds(UUID projectId) {
    return jdbcTemplate.queryForList(
        "SELECT tech_label_id FROM project_tech_labels WHERE project_id = ? ORDER BY tech_label_id",
        UUID.class,
        projectId);
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

  private record StateCounts(long projects, long labels, long joins, long votes) {}

  private record TopProjectsFixture(User viewer, List<Project> projects) {}
}
