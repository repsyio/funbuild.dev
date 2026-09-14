package dev.funbuild.security;

import dev.funbuild.user.AuthProvider;
import dev.funbuild.user.User;
import dev.funbuild.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Finds or creates the local user from the OAuth2 profile and redirects to the SPA with a JWT. */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final UserService userService;
  private final JwtService jwtService;
  private final String frontendUrl;

  public OAuth2LoginSuccessHandler(
      UserService userService, JwtService jwtService, @Value("${app.frontend-url}") String frontendUrl) {
    this.userService = userService;
    this.jwtService = jwtService;
    this.frontendUrl = frontendUrl;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
    OAuth2User oAuth2User = oauthToken.getPrincipal();
    AuthProvider provider = AuthProvider.valueOf(oauthToken.getAuthorizedClientRegistrationId().toUpperCase());

    String providerId =
        provider == AuthProvider.GOOGLE
            ? oAuth2User.getAttribute("sub")
            : String.valueOf(oAuth2User.getAttributes().get("id"));
    String email = oAuth2User.getAttribute("email");
    String name = oAuth2User.getAttribute("name");
    String avatarUrl =
        provider == AuthProvider.GOOGLE
            ? oAuth2User.getAttribute("picture")
            : oAuth2User.getAttribute("avatar_url");

    User user = userService.findOrCreateOAuthUser(provider, providerId, email, name, avatarUrl);
    String token = jwtService.generateToken(user);

    String redirect =
        UriComponentsBuilder.fromUriString(frontendUrl + "/oauth-callback")
            .fragment("token=" + token)
            .build()
            .toUriString();
    response.sendRedirect(redirect);
  }
}
