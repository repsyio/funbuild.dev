package dev.funbuild.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import io.repsy.core.response.dtos.ResponseType;
import io.repsy.core.response.dtos.RestResponse;
import io.repsy.core.response.services.RestResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

  @Test
  void convertsCommonExceptionsToResponseEnvelopes() {
    RestResponseFactory responses = mock(RestResponseFactory.class);
    RestResponse<Object> body = new RestResponse<>("error", ResponseType.ERROR);
    when(responses.error(anyString())).thenReturn(body);
    GlobalExceptionHandler handler = new GlobalExceptionHandler(responses);

    assertThat(handler.handleNotFound(new NotFoundException("missing")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(handler.handleUnauthorized(new UnauthorizedException("unauthorized")).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(handler.handleBadRequest(new IllegalArgumentException("invalid")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void convertsUnexpectedExceptionsToInternalErrorResponse() {
    RestResponseFactory responses = mock(RestResponseFactory.class);
    RestResponse<Object> body = new RestResponse<>("internal.error", ResponseType.ERROR);
    when(responses.error("internal.error")).thenReturn(body);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/test");
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    var result = new GlobalExceptionHandler(responses).handleUnexpected(new RuntimeException("boom"), request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isSameAs(body);
  }
}
