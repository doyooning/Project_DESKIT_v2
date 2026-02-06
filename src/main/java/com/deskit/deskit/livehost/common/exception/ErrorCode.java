package com.deskit.deskit.livehost.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 1. 공통 에러
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),
    FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "C003", "접근 권한이 없습니다."), // 공통 권한 에러로 통합

    // 2. 인증/권한 에러 (Member 관련)
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "회원을 찾을 수 없습니다."),
    UNAUTHORIZED_MEMBER(HttpStatus.UNAUTHORIZED, "M002", "로그인이 필요합니다."),


    // 3. JWT 토큰 관련 에러
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A005", "리프레시 토큰이 없습니다."),

    // 4. 판매자(Seller) 에러
    SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "판매자를 찾을 수 없습니다."),

    // 5. 카테고리(Category) 에러
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CT001", "카테고리를 찾을 수 없습니다."),

    // 6. 방송(Broadcast) 에러
    BROADCAST_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "방송을 찾을 수 없습니다."),
    BROADCAST_NOT_ON_AIR(HttpStatus.BAD_REQUEST, "B002", "방송 중인 상태가 아닙니다."),
    BROADCAST_STOPPED_BY_ADMIN(HttpStatus.FORBIDDEN, "B003", "관리자에 의해 중단된 방송입니다."),
    BROADCAST_INVALID_TRANSITION(HttpStatus.BAD_REQUEST, "B006", "방송 상태 전환이 올바르지 않습니다."),
    BROADCAST_ALREADY_SANCTIONED(HttpStatus.FORBIDDEN, "B007", "제재된 시청자는 이용할 수 없습니다."),

    // 예약 관련
    RESERVATION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "B004", "예약은 최대 7개까지만 가능합니다."),
    BROADCAST_SLOT_FULL(HttpStatus.BAD_REQUEST, "B005", "해당 시간대의 예약 슬롯이 꽉 찼습니다."), // 3개 제한

    // 7. 상품(Product) 에러
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "상품을 찾을 수 없습니다."),
    PRODUCT_SOLD_OUT(HttpStatus.BAD_REQUEST, "P002", "상품 재고가 부족합니다."), // 통합
    PRODUCT_PIN_ERROR(HttpStatus.BAD_REQUEST, "P003", "품절된 상품은 핀 설정이 불가능합니다."),

    // 8. OpenVidu / Live 에러
    OPENVIDU_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "O001", "OpenVidu 서버 연동 오류가 발생했습니다."),
    RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "방송 결과 리포트가 존재하지 않습니다."),

    // 9. VOD 에러
    VOD_NOT_FOUND(HttpStatus.NOT_FOUND, "V001", "VOD를 찾을 수 없습니다."),
    CANNOT_OPEN_STOPPED_BROADCAST(HttpStatus.BAD_REQUEST, "V002", "제재된 방송은 공개로 전환할 수 없습니다."),
    VOD_ADMIN_LOCKED(HttpStatus.FORBIDDEN, "V003", "관리자에 의해 비공개 처리된 VOD는 공개로 전환할 수 없습니다."),

    // 10. 파일 업로드 / S3 에러
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "F001", "파일 업로드에 실패했습니다."),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "F002", "지원하지 않는 파일 형식입니다. (jpg, png, gif만 가능)"),
    FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "F003", "파일 삭제에 실패했습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "F004", "파일 크기가 제한을 초과했습니다."),
    INVALID_IMAGE_RATIO(HttpStatus.BAD_REQUEST, "F005", "이미지 비율이 올바르지 않습니다."),

    // 11. 시스템 에러
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "SY001", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
