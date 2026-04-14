package com.knowledgemanager.common.exception;

import com.knowledgemanager.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("Business exception: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<?> handleUnauthorizedException(UnauthorizedException e, HttpServletRequest request) {
        log.error("Unauthorized exception: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.unauthorized(e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<?> handleForbiddenException(ForbiddenException e, HttpServletRequest request) {
        log.error("Forbidden exception: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.forbidden(e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<?> handleNotFoundException(NotFoundException e, HttpServletRequest request) {
        log.error("Not found exception: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.notFound(e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleValidationException(ValidationException e, HttpServletRequest request) {
        log.error("Validation exception: {} - {}", request.getRequestURI(), e.getMessage());
        return ApiResponse.badRequest(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ApiResponse.badRequest(message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ApiResponse.badRequest(message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleException(Exception e, HttpServletRequest request) {
        log.error("Unexpected error: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return ApiResponse.error("服务器内部错误: " + e.getMessage());
    }
}
