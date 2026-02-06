package com.deskit.deskit.livehost.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException { // 사용자 정의 예외

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
