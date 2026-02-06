package com.deskit.deskit.livehost.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResult<T> {
    // 불변성을 위해서 final로 변수 선언
    private final boolean success;
    private final T data;
    private final ErrorResponse error;

    // [성공 시] ApiResult.success(데이터) 호출
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(true, data, null);
    }

    // [실패 시] ApiResult.error(에러코드) 호출
    public static <T> ApiResult<T> error(ErrorCode errorCode) {
        return new ApiResult<>(false, null, new ErrorResponse(errorCode));
    }
}
