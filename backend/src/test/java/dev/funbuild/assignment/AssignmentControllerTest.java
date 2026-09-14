package dev.funbuild.assignment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.funbuild.security.SecurityConfig;
import dev.funbuild.security.SecurityTestConfig;
import dev.funbuild.user.AuthProvider;
import dev.funbuild.user.Role;
import dev.funbuild.user.User;
import dev.funbuild.user.UserService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AssignmentController.class)
@Import({SecurityConfig.class, SecurityTestConfig.class})
class AssignmentControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private dev.funbuild.security.JwtService jwtService;

  @MockitoBean private AssignmentService assignmentService;
  @MockitoBean private UserService userService;

  private static final User ADMIN = new User("admin@funbuild.dev", null, "Admin", null, Role.ADMIN, AuthProvider.LOCAL, null);
  private static final User MEMBER = new User("member@funbuild.dev", null, "Member", null, Role.MEMBER, AuthProvider.LOCAL, null);

  @Test
  void listsAssignments() throws Exception {
    Assignment assignment =
        new Assignment(
            "Mini racing game",
            "Build a small racing game in two weeks.",
            Instant.now().minusSeconds(3600),
            Instant.now().plusSeconds(3600),
            ADMIN);
    given(assignmentService.listActive()).willReturn(List.of(AssignmentResponse.from(assignment)));

    mvc.perform(get("/api/assignments").param("status", "active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Mini racing game"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  void unauthenticatedCannotCreateAssignment() throws Exception {
    mvc.perform(
            post("/api/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void memberCannotCreateAssignment() throws Exception {
    mvc.perform(
            post("/api/assignments")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(MEMBER, 2L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateAssignment() throws Exception {
    given(userService.get(1L)).willReturn(ADMIN);
    given(assignmentService.create(any(), any()))
        .willReturn(
            AssignmentResponse.from(
                new Assignment(
                    "Mini racing game",
                    "Build a small racing game.",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    ADMIN)));

    mvc.perform(
            post("/api/assignments")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Mini racing game"));
  }

  @Test
  void allowsConfiguredOriginInCorsPreflight() throws Exception {
    mvc.perform(
            options("/api/assignments")
                .header(HttpHeaders.ORIGIN, SecurityTestConfig.TEST_FRONTEND_URL)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SecurityTestConfig.TEST_FRONTEND_URL));
  }

  @Test
  void rejectsOtherOriginsInCorsPreflight() throws Exception {
    mvc.perform(
            options("/api/assignments")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden());
  }

  private String bearerFor(User user, long id) {
    return "Bearer " + jwtService.generateToken(id, user.getEmail(), user.getDisplayName(), user.getRole());
  }

  private String validRequestJson() {
    return """
        {
          "title": "Mini racing game",
          "description": "Build a small racing game in two weeks.",
          "startAt": "%s",
          "endAt": "%s"
        }
        """
        .formatted(Instant.now(), Instant.now().plusSeconds(3600));
  }
}
