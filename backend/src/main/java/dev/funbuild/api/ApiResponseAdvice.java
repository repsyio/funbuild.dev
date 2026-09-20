package dev.funbuild.api;

import io.repsy.core.response.dtos.RestResponse;
import io.repsy.core.response.services.RestResponseFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Wraps successful API payloads without forcing every feature controller to know the envelope. */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

  private final RestResponseFactory responses;

  public ApiResponseAdvice(final RestResponseFactory responses) {
    this.responses = responses;
  }

  @Override
  public boolean supports(
      final MethodParameter returnType,
      final Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      final Object body,
      final MethodParameter returnType,
      final MediaType selectedContentType,
      final Class<? extends HttpMessageConverter<?>> selectedConverterType,
      final ServerHttpRequest request,
      final ServerHttpResponse response) {
    if (!this.isApiRequest(request) || body == null || body instanceof RestResponse<?>) {
      return body;
    }
    return this.responses.success("api.success", body);
  }

  private boolean isApiRequest(final ServerHttpRequest request) {
    return request.getURI().getPath().startsWith("/api/");
  }
}
