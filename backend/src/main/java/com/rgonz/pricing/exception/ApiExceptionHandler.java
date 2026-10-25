package com.rgonz.pricing.exception;

import com.rgonz.pricing.config.PriceProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final PriceProperties priceProperties;

    public ApiExceptionHandler(PriceProperties priceProperties) {
        this.priceProperties = priceProperties;
    }

    public record InvalidField(String field, String message) {}

    @ExceptionHandler(ProductRuleNotFoundException.class)
    public ProblemDetail handleNotFound(ProductRuleNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "PRODUCT_RULE_NOT_FOUND", e.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ProblemDetail handleDuplicateSku(DuplicateSkuException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "SKU_ALREADY_EXISTS", e.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(SkuInactiveException.class)
    public ProblemDetail handleInactiveSku(SkuInactiveException e, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "SKU_INACTIVE", e.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ProvidersUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleProvidersUnavailable(
            ProvidersUnavailableException e, HttpServletRequest request) {
        var body = problem(
                HttpStatus.SERVICE_UNAVAILABLE, "PROVIDERS_UNAVAILABLE", e.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(
                        HttpHeaders.RETRY_AFTER,
                        String.valueOf(priceProperties.unavailableRetryAfter().toSeconds()))
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("unexpected error path={}", request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error inesperado", request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new InvalidField(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationFailed(errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (e.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            return validationFailed(
                    List.of(new InvalidField(fieldPath(mismatch), "tiene un valor no válido")), request);
        }
        var body =
                problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "El cuerpo no es un JSON válido", path(request));
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Object> validationFailed(List<InvalidField> errors, WebRequest request) {
        var body =
                problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "La regla tiene campos inválidos", path(request));
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, String path) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        return problem;
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private static String fieldPath(MismatchedInputException mismatch) {
        return mismatch.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .collect(Collectors.joining("."));
    }
}
