package dev.funbuild.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

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
      OAuth2LoginFailureHandler oAuth2LoginFailureHandler)
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
        .exceptionHandling(e -> e.authenticationEntryPoint(unauthorizedEntryPoint()))
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler))
        .addFilterBefore(jwtAuthFilter, AuthorizationFilter.class);
    return http.build();
  }

  private AuthenticationEntryPoint unauthorizedEntryPoint() {
    return (request, response, authException) -> {
      response.setStatus(401);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"message\":\"Unauthorized\"}");
    };
  }
}
