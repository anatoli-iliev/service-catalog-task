package com.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalog.api.dto.ErrorResponse;
import com.catalog.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404() {
        var ex = new ResourceNotFoundException("Application", "app-123");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).contains("Application not found: app-123");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleBadRequest_returns400() {
        var ex = new IllegalArgumentException("Invalid SemVer format: abc");

        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Invalid SemVer format: abc");
    }

    @Test
    void handleGeneral_returns500_withGenericMessage() {
        var ex = new RuntimeException("Internal DB connection failed");

        ResponseEntity<ErrorResponse> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void handleNoResource_returns404() {
        var ex = new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/v1/unknown", null);

        ResponseEntity<ErrorResponse> response = handler.handleNoResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Resource not found");
    }

    @Test
    void handleTypeMismatch_returns400() {
        var ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "page", null, new NumberFormatException("abc"));

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("page").contains("abc");
    }

    @Test
    void handleMethodNotAllowed_returns405() {
        var ex = new HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotAllowed(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().status()).isEqualTo(405);
    }

    @Test
    void handleGeneral_doesNotLeakExceptionDetails() {
        var ex = new RuntimeException("password=secret123 host=db.internal");

        ResponseEntity<ErrorResponse> response = handler.handleGeneral(ex);

        assertThat(response.getBody().message()).doesNotContain("secret123");
        assertThat(response.getBody().message()).doesNotContain("db.internal");
    }
}
