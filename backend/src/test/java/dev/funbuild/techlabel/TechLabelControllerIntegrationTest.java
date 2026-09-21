package dev.funbuild.techlabel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.funbuild.user.AuthProvider;
import dev.funbuild.user.Role;
import dev.funbuild.user.User;
import dev.funbuild.user.UserRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class TechLabelControllerIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @Autowired private MockMvc mvc;
  @Autowired private TechLabelRepository techLabelRepository;
  @Autowired private UserRepository userRepository;

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "  \t  "})
  void publicBlankQueriesReturnAllLabelsInNameOrder(String query) throws Exception {
    seedLabels("Zig", "Alpha", "Middle");

    MockHttpServletRequestBuilder request = get("/api/tech-labels");
    if (query != null) {
      request.param("q", query);
    }

    mvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SUCCESS"))
        .andExpect(jsonPath("$.data[*].name").value(contains("Alpha", "Middle", "Zig")))
        .andExpect(jsonPath("$.data[0].id").isString())
        .andExpect(jsonPath("$.data[0].createdBy").doesNotExist());
  }

  @Test
  void trimsQueryAndMatchesCaseInsensitiveSubstrings() throws Exception {
    seedLabels("JavaScript", "TypeScript", "Rust");

    mvc.perform(get("/api/tech-labels").param("q", "  sCrIpT  "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].name").value(contains("JavaScript", "TypeScript")));
  }

  @Test
  void treatsLikeSpecialCharactersAsLiteralText() throws Exception {
    seedLabels("C++", "100% Real", "1000 Real", "C#");

    mvc.perform(get("/api/tech-labels").param("q", "++"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].name").value(contains("C++")));

    mvc.perform(get("/api/tech-labels").param("q", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].name").value(contains("100% Real")));
  }

  @Test
  void emptySearchReturnsNoResultsWithoutCreatingLabels() throws Exception {
    seedLabels("Existing");
    long countBefore = techLabelRepository.count();

    mvc.perform(get("/api/tech-labels").param("q", "does-not-exist"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("SUCCESS"))
        .andExpect(jsonPath("$.data").isEmpty());

    assertThat(techLabelRepository.count()).isEqualTo(countBefore);
  }

  @Test
  void responseContainsOnlyPublicLabelFields() throws Exception {
    seedLabels("Public Label");

    mvc.perform(get("/api/tech-labels"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("secret"))))
        .andExpect(jsonPath("$.data[0].id").isString())
        .andExpect(jsonPath("$.data[0].name").value("Public Label"))
        .andExpect(jsonPath("$.data[0].createdBy").doesNotExist())
        .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
  }

  private void seedLabels(String... names) {
    User creator =
        userRepository.saveAndFlush(
            new User(
                "tech-labels@example.com",
                "secret-password-hash",
                "Tech Label Creator",
                null,
                Role.MEMBER,
                AuthProvider.LOCAL,
                null));
    techLabelRepository.saveAll(
        Stream.of(names).map(name -> new TechLabel(name, creator)).toList());
    techLabelRepository.flush();
  }
}
