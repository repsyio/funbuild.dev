package dev.funbuild.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.funbuild.security.AuthenticatedUser;
import dev.funbuild.security.JwtService;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AuthControllerIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @Autowired private MockMvc mvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;

  @Test
  void registersUserWithJwtAndPersistedDefaults() throws Exception {
    String response =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerRequest("alice@example.com", "correct-horse-battery")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isString())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.id").isString())
            .andExpect(jsonPath("$.user.id").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("alice@example.com"))
            .andExpect(jsonPath("$.user.displayName").value("alice"))
            .andExpect(jsonPath("$.user.role").value("MEMBER"))
            .andExpect(jsonPath("$.user.authProvider").value("LOCAL"))
            .andExpect(jsonPath("$.user.password").doesNotExist())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andExpect(content().string(not(containsString("correct-horse-battery"))))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String token = JsonPath.read(response, "$.token");
    AuthenticatedUser claims = jwtService.parse(token).orElseThrow();
    User persisted = userRepository.findByEmailIgnoreCase("alice@example.com").orElseThrow();

    assertThat(claims.id()).isEqualTo(persisted.getId());
    assertThat(claims.email()).isEqualTo("alice@example.com");
    assertThat(claims.displayName()).isEqualTo("alice");
    assertThat(claims.role()).isEqualTo(Role.MEMBER);
    assertThat(persisted.getDisplayName()).isEqualTo("alice");
    assertThat(persisted.getPasswordHash()).isNotBlank().isNotEqualTo("correct-horse-battery");
  }

  @Test
  void rejectsMalformedEmail() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequest("not-an-email", "correct-horse-battery")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.email").exists());
  }

  @ParameterizedTest
  @MethodSource("invalidPasswordRequests")
  void rejectsInvalidPassword(String request) throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.password").exists());
  }

  private static Stream<Arguments> invalidPasswordRequests() {
    return Stream.of(
        Arguments.of("{\"email\":\"missing@example.com\"}"),
        Arguments.of("{\"email\":\"blank@example.com\",\"password\":\"\"}"),
        Arguments.of("{\"email\":\"whitespace@example.com\",\"password\":\"   \"}"),
        Arguments.of("{\"email\":\"short@example.com\",\"password\":\"short\"}"));
  }

  @Test
  void rejectsDuplicateEmailWithConflict() throws Exception {
    String request = registerRequest("duplicate@example.com", "correct-horse-battery");

    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isOk());

    userRepository.flush();

    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Email is already registered"));
  }

  private static String registerRequest(String email, String password) {
    return """
        {"email":"%s","password":"%s"}
        """.formatted(email, password);
  }
}
