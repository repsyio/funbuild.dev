package dev.funbuild.security;

import dev.funbuild.user.UserService;
import dev.funbuild.user.UserRepository;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Supplies the beans {@link SecurityConfig}'s filter chain needs, without a real database.
 * Import alongside {@code UserService} as a {@code @MockitoBean} in @WebMvcTest slices.
 */
@TestConfiguration
public class SecurityTestConfig {

  public static final String TEST_JWT_SECRET = "test-secret-test-secret-test-secret-1234567890";
  public static final String TEST_FRONTEND_URL = "http://localhost:4200";

  @Bean
  public JwtService jwtService() {
    return new JwtService(TEST_JWT_SECRET, 60);
  }

  @Bean
  public JwtAuthFilter jwtAuthFilter(
      JwtService jwtService,
      UserRepository userRepository,
      SecurityContextRepository securityContextRepository) {
    return new JwtAuthFilter(jwtService, userRepository, securityContextRepository);
  }

  @Bean
  public CustomOAuth2UserService customOAuth2UserService() {
    return new CustomOAuth2UserService();
  }

  @Bean
  public OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler(UserService userService, JwtService jwtService) {
    return new OAuth2LoginSuccessHandler(userService, jwtService, TEST_FRONTEND_URL);
  }

  @Bean
  public OAuth2LoginFailureHandler oAuth2LoginFailureHandler() {
    return new OAuth2LoginFailureHandler(TEST_FRONTEND_URL);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(TEST_FRONTEND_URL));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
