package com.deskit.deskit.livehost.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;

@Slf4j
@RestControllerAdvice(basePackages = "com.deskit.deskit.livehost.controller")
@Order(Ordered.HIGHEST_PRECEDENCE) // 혹시 deskit의 전역 핸들러가 있다면, 이게 먼저 실행되도록 우선순위 높임
public class LiveExceptionHandler extends ResponseEntityExceptionHandler {

    // 1. 비즈니스 로직 에러 (LiveBusinessException 처리)
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResult<?>> handleBusinessException(BusinessException e) {
        log.error("[Live Error] BusinessException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResult.error(e.getErrorCode()));
    }

    // 2. 입력값 유효성 검사 실패 (@Valid)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        log.error("[Live Error] Validation Error: {}", e.getMessage());
        return ResponseEntity
                .status(400)
                .body(ApiResult.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    // 3. 그 외 알 수 없는 에러 (내 영역 안에서 터진 것만)
    @ExceptionHandler(IOException.class)
    protected ResponseEntity<Void> handleIOException(IOException e) {
        log.debug("[Live Error] Client disconnected: {}", e.getMessage());
        return ResponseEntity.noContent().build();
    }

    // 4. 그 외 알 수 없는 에러 (내 영역 안에서 터진 것만)
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResult<?>> handleException(Exception e) {
        log.error("[Live Error] Unknown Exception: ", e);
        return ResponseEntity
                .status(500)
                .body(ApiResult.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
