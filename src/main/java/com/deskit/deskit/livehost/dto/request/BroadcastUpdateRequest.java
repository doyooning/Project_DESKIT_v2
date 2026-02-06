package com.deskit.deskit.livehost.dto.request;

import com.deskit.deskit.livehost.common.enums.BroadcastLayout;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class BroadcastUpdateRequest {
    @NotBlank(message = "방송 제목은 필수입니다.")
    @Size(max = 30, message = "방송 제목은 최대 30자까지 입력 가능합니다.")
    private String title;

    @Size(max = 100, message = "공지사항은 최대 100자까지 입력 가능합니다.")
    private String notice;

    @NotNull(message = "카테고리는 필수 선택 사항입니다.")
    private Long categoryId;

    @Future(message = "방송 예약 시간은 현재 시간보다 미래여야 합니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime scheduledAt;

    @NotBlank(message = "방송 썸네일은 필수입니다.")
    private String thumbnailUrl;

    private String waitScreenUrl;

    @NotNull(message = "방송 레이아웃 설정은 필수입니다.")
    private BroadcastLayout broadcastLayout;

    // --- 연관 데이터 (교체 방식이므로 유효성 검사 필수) ---

    @Valid
    @NotNull(message = "판매할 상품을 최소 1개 이상 선택해야 합니다.")
    @Size(min = 1, max = 10, message = "판매 상품은 1개 이상, 10개 이하로 등록 가능합니다.")
    private List<BroadcastProductRequest> products;

    @Valid
    @Size(max = 10, message = "큐카드는 최대 10개까지 등록 가능합니다.")
    private List<QcardRequest> qcards;
}
