package dev.funbuild.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.repsy.core.response.dtos.ResponseType;
import io.repsy.core.response.dtos.RestResponse;
import io.repsy.core.response.services.RestResponseFactory;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

class ApiResponseAdviceTest {

  private final RestResponseFactory responses = mock(RestResponseFactory.class);
  private final ApiResponseAdvice advice = new ApiResponseAdvice(responses);
  private final ServerHttpResponse response = mock(ServerHttpResponse.class);

  @Test
  void wrapsSuccessfulApiPayloads() {
    Object payload = new Object();
    RestResponse<Object> envelope = new RestResponse<>("api.success", ResponseType.SUCCESS);
    when(responses.success(anyString(), same(payload))).thenReturn(envelope);

    Object result = advice.beforeBodyWrite(payload, null, null, null, request("/api/projects"), response);

    assertThat(result).isSameAs(envelope);
  }

  @Test
  void leavesNonApiAndAlreadyEnvelopedResponsesUntouched() {
    Object payload = new Object();
    RestResponse<Object> envelope = new RestResponse<>("api.success", ResponseType.SUCCESS);

    assertThat(advice.beforeBodyWrite(payload, null, null, null, request("/health"), response))
        .isSameAs(payload);
    assertThat(advice.beforeBodyWrite(null, null, null, null, request("/api/projects"), response)).isNull();
    assertThat(advice.beforeBodyWrite(envelope, null, null, null, request("/api/projects"), response))
        .isSameAs(envelope);
  }

  @Test
  void supportsAllResponseTypes() {
    assertThat(advice.supports(null, null)).isTrue();
  }

  private ServerHttpRequest request(String path) {
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getURI()).thenReturn(URI.create(path));
    return request;
  }
}
