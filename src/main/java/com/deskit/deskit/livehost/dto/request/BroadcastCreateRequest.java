package com.deskit.deskit.livehost.dto.request;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.tag.entity.TagCategory;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.common.enums.BroadcastLayout;
import com.deskit.deskit.livehost.common.enums.BroadcastStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class BroadcastCreateRequest {

    @NotBlank(message = "방송 제목은 필수입니다.")
    @Size(max = 30, message = "방송 제목은 최대 30자까지 입력 가능합니다.")
    private String title;

    @Size(max = 100, message = "공지사항은 최대 100자까지 입력 가능합니다.")
    private String notice;

    @NotNull(message = "카테고리는 필수 선택 사항입니다.")
    private Long categoryId;

    @NotNull(message = "방송 예약 시간은 필수입니다.")
    @Future(message = "방송 예약 시간은 현재 시간보다 미래여야 합니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime scheduledAt;

    @NotBlank(message = "방송 썸네일은 필수입니다.")
    private String thumbnailUrl;

    private String waitScreenUrl;

    @NotNull(message = "방송 레이아웃 설정은 필수입니다.")
    private BroadcastLayout broadcastLayout;

    @Valid
    @NotNull(message = "판매 상품은 최소 1개 이상 등록해야 합니다.")
    @Size(min = 1, max = 10, message = "판매 상품은 1개 ~ 10개까지 등록 가능합니다.")
    private List<BroadcastProductRequest> products;

    @Valid
    @Size(max = 10, message = "큐카드는 최대 10개까지 등록 가능합니다.")
    private List<QcardRequest> qcards;

    // Entity 변환
    public Broadcast toEntity(Seller seller, TagCategory tagCategory) {
        return Broadcast.builder()
                .seller(seller)
                .tagCategory(tagCategory)
                .broadcastTitle(this.title)
                .broadcastNotice(this.notice)
                .scheduledAt(this.scheduledAt)
                .broadcastThumbUrl(this.thumbnailUrl)
                .broadcastWaitUrl(this.waitScreenUrl)
                .broadcastLayout(this.broadcastLayout)
                .status(BroadcastStatus.RESERVED)
                .build();
    }
}