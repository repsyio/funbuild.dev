package dev.funbuild.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Only used for the plain-OAuth2 GitHub registration (Google's "openid" scope routes it through
 * the OIDC user service instead, which already returns email/name/picture claims as-is).
 *
 * <p>GitHub's /user endpoint only returns a public "email" attribute when the account has one
 * set, so this falls back to /user/emails (needs the "user:emails" scope) for the verified
 * primary email.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final RestClient restClient = RestClient.create();

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User user = super.loadUser(userRequest);
    if (!"github".equals(userRequest.getClientRegistration().getRegistrationId())
        || user.getAttributes().get("email") != null) {
      return user;
    }

    String primaryEmail = fetchGithubPrimaryEmail(userRequest.getAccessToken().getTokenValue());
    if (primaryEmail == null) {
      return user;
    }
    Map<String, Object> attributes = new LinkedHashMap<>(user.getAttributes());
    attributes.put("email", primaryEmail);
    return new DefaultOAuth2User(user.getAuthorities(), attributes, "id");
  }

  private String fetchGithubPrimaryEmail(String accessToken) {
    List<Map<String, Object>> emails =
        restClient
            .get()
            .uri("https://api.github.com/user/emails")
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    if (emails == null) {
      return null;
    }
    return emails.stream()
        .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
        .map(e -> (String) e.get("email"))
        .findFirst()
        .orElse(null);
  }
}
