package dev.funbuild.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.funbuild.error.ConflictException;
import dev.funbuild.security.JwtService;
import dev.funbuild.security.SecurityConfig;
import dev.funbuild.security.SecurityTestConfig;
import dev.funbuild.user.Role;
import dev.funbuild.user.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import({SecurityConfig.class, SecurityTestConfig.class})
class ProjectControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;

  @MockitoBean private ProjectService projectService;
  @MockitoBean private UserService userService;

  @Test
  void listIsPublic() throws Exception {
    given(projectService.list(null, null)).willReturn(List.of());

    mvc.perform(get("/api/projects")).andExpect(status().isOk());
  }

  @Test
  void unauthenticatedCannotSubmitProject() throws Exception {
    mvc.perform(
            post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void submittingToExpiredAssignmentIsRejected() throws Exception {
    given(projectService.submit(any(), any()))
        .willThrow(new ConflictException("This assignment is no longer accepting submissions"));

    mvc.perform(
            post("/api/projects")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("ERROR"))
        .andExpect(jsonPath("$.text").value("This assignment is no longer accepting submissions"))
        .andExpect(jsonPath("$.errorCode").isNotEmpty());
  }

  private String bearer() {
    return "Bearer "
        + jwtService.generateToken(UUID.randomUUID(), "member@funbuild.dev", "Member", Role.MEMBER);
  }

  private String validRequestJson() {
    return """
        {
          "assignmentId": "0198f4d2-6a3b-7c9e-8f21-1a2b3c4d5e6f",
          "title": "Turbo Kart",
          "description": "A tiny top-down racer built with Canvas.",
          "showcaseUrl": "https://turbokart.example.com",
          "gitRepoUrl": "https://github.com/example/turbokart",
          "techLabels": ["TypeScript", "Canvas"]
        }
        """;
  }
}
