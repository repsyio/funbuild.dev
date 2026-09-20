package dev.funbuild.security;

import io.repsy.core.response.services.RestResponseFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Per-request only (no HTTP session): {@link JwtAuthFilter} re-derives the principal from the
   * bearer token on every request, so there's nothing to persist beyond the current request.
   */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new RequestAttributeSecurityContextRepository();
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      CorsConfigurationSource corsConfigurationSource,
      SecurityContextRepository securityContextRepository,
      JwtAuthFilter jwtAuthFilter,
      CustomOAuth2UserService customOAuth2UserService,
      OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
      OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
      AuthenticationEntryPoint unauthorizedEntryPoint,
      AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/actuator/**", "/api/auth/**", "/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/assignments/**",
                        "/api/projects/**",
                        "/api/tech-labels/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(unauthorizedEntryPoint).accessDeniedHandler(accessDeniedHandler))
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler))
        .addFilterBefore(jwtAuthFilter, AuthorizationFilter.class);
    return http.build();
  }

  @Bean
  AuthenticationEntryPoint unauthorizedEntryPoint(RestResponseFactory responses, ObjectMapper objectMapper) {
    return (request, response, authException) ->
        writeError(response, objectMapper, responses.error("auth.unauthorized"), HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Bean
  AccessDeniedHandler accessDeniedHandler(RestResponseFactory responses, ObjectMapper objectMapper) {
    return (request, response, accessDeniedException) ->
        writeError(response, objectMapper, responses.error("auth.forbidden"), HttpServletResponse.SC_FORBIDDEN);
  }

  private void writeError(
      HttpServletResponse response, ObjectMapper objectMapper, Object body, int status) throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
