package dev.funbuild.security;

import dev.funbuild.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final SecurityContextRepository securityContextRepository;

  public JwtAuthFilter(
      JwtService jwtService,
      UserRepository userRepository,
      SecurityContextRepository securityContextRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
    this.securityContextRepository = securityContextRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring("Bearer ".length());
      jwtService
          .parse(token)
          .flatMap(principal -> userRepository.findById(principal.id()))
          .ifPresent(
              user -> {
                AuthenticatedUser principal =
                    new AuthenticatedUser(
                        user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
                var authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                // AuthorizationFilter resolves its deferred Authentication supplier from this
                // repository rather than re-reading SecurityContextHolder, so the mutation above
                // alone is invisible downstream unless it's also saved here explicitly.
                securityContextRepository.saveContext(context, request, response);
              });
    }
    filterChain.doFilter(request, response);
  }
}
