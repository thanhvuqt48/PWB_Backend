package com.fpt.producerworkbench.exception;

import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fpt.producerworkbench.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.io.IOException;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.WebRequest;
import vn.payos.exception.WebhookException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String MIN_ATTRIBUTE = "min";

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse> handleException(Exception e) {
        log.error("An uncategorized error occurred: ", e);

        return ResponseEntity.badRequest()
                .body(ApiResponse.builder()
                        .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                        .message(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage())
                        .build());
    }

    @ExceptionHandler(AppException.class)
    ResponseEntity<ErrorResponse> handleAppException(AppException exception, WebRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode.getCode())
                .timestamp(new Date())
                .error(errorCode.getHttpStatus().getReasonPhrase())
                .message(errorCode.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String defaultMessage = e.getFieldError() != null ? e.getFieldError().getDefaultMessage() : null;
        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        Map<String, Object> attributes = null;

        try {
            if (defaultMessage != null) {
                errorCode = ErrorCode.valueOf(defaultMessage);

                var constraintViolation = e.getBindingResult().getAllErrors().get(0).unwrap(ConstraintViolation.class);

                attributes = constraintViolation.getConstraintDescriptor().getAttributes();

                log.info(attributes.toString());

                ApiResponse apiResponse = new ApiResponse();
                apiResponse.setCode(errorCode.getCode());
                apiResponse.setMessage(
                        Objects.nonNull(attributes)
                                ? mapAttribute(errorCode.getMessage(), attributes)
                                : errorCode.getMessage());

                return ResponseEntity.badRequest().body(apiResponse);
            }
        } catch (IllegalArgumentException iae) {
            log.debug("Validation message is not an ErrorCode key, returning raw message: {}", defaultMessage);
        }

        ApiResponse fallback = ApiResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.getCode())
                .message(defaultMessage)
                .build();

        return ResponseEntity.badRequest().body(fallback);
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    ResponseEntity<ApiResponse> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();

        String errorMessage = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", errorMessage);

        ApiResponse response = ApiResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.getCode())
                .message(errorMessage)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();

        if (cause instanceof InvalidFormatException invalidFormat) {
            String field = "";
            if (invalidFormat.getPath() != null && !invalidFormat.getPath().isEmpty()) {
                JsonMappingException.Reference ref = invalidFormat.getPath().get(invalidFormat.getPath().size() - 1);
                field = ref != null && ref.getFieldName() != null ? ref.getFieldName() : "";
            }

            Class<?> targetType = invalidFormat.getTargetType();
            if (targetType != null && targetType.isEnum()) {
                Object[] constants = targetType.getEnumConstants();
                StringBuilder allowed = new StringBuilder();
                for (int i = 0; i < constants.length; i++) {
                    allowed.append(constants[i].toString());
                    if (i < constants.length - 1) allowed.append(", ");
                }

                String provided = String.valueOf(invalidFormat.getValue());
                String baseMessage = (provided == null || provided.isEmpty() || "\"\"".equals(provided))
                        ? "Trường '" + field + "' không được bỏ trống."
                        : "Giá trị '" + provided + "' không hợp lệ cho trường '" + field + "'.";

                String message = baseMessage + " Giá trị hợp lệ: " + allowed;

                ApiResponse apiResponse = ApiResponse.builder()
                        .code(ErrorCode.VALIDATION_FAILED.getCode())
                        .message(message)
                        .build();

                return ResponseEntity.badRequest().body(apiResponse);
            }
        }

        ApiResponse fallback = ApiResponse.builder()
                .code(ErrorCode.VALIDATION_FAILED.getCode())
                .message("Dữ liệu yêu cầu không đọc được hoặc không hợp lệ.")
                .build();
        return ResponseEntity.badRequest().body(fallback);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.BAD_REQUEST.getCode())
                .timestamp(new Date())
                .error(ErrorCode.BAD_REQUEST.getHttpStatus().getReasonPhrase())
                .message("Thiếu tham số yêu cầu: " + e.getParameterName())
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.BAD_REQUEST.getCode())
                .timestamp(new Date())
                .error(ErrorCode.BAD_REQUEST.getHttpStatus().getReasonPhrase())
                .message("Phương thức yêu cầu không được hỗ trợ.")
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(WebhookException.class)
    ResponseEntity<ErrorResponse> handleWebhookException(WebhookException e) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.BAD_REQUEST.getCode())
                .timestamp(new Date())
                .error(ErrorCode.BAD_REQUEST.getHttpStatus().getReasonPhrase())
                .message("Lỗi webhook: " + e.getMessage())
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(IOException.class)
    ResponseEntity<ApiResponse> handleIOException(IOException e) {
        log.error("IO Exception occurred: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.builder()
                        .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                        .message("Lỗi xử lý file: " + (e.getMessage() != null ? e.getMessage() : "Không thể đọc/ghi file"))
                        .build());
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiResponse> handleRuntimeException(RuntimeException e) {
        log.error("Runtime Exception occurred: ", e);
        
        // Kiểm tra nếu là lỗi từ VNPT API
        String message = e.getMessage();
        if (message != null && (message.contains("VNPT") || message.contains("VNPT API"))) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.builder()
                            .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                            .message("Lỗi kết nối đến dịch vụ xác thực: " + message)
                            .build());
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.builder()
                        .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                        .message(message != null ? message : "Đã có lỗi xảy ra. Vui lòng thử lại sau.")
                        .build());
    }

    private String mapAttribute(String message, Map<String, Object> attributes) {
        String minValue = String.valueOf(attributes.get(MIN_ATTRIBUTE));

        return message.replace("{" + MIN_ATTRIBUTE + "}", minValue);
    }


}
