package com.deskit.deskit.livehost.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QcardRequest {
    @NotBlank(message = "질문 내용은 필수입니다.")
    @Size(max = 50, message = "큐카드 질문은 최대 50자까지 입력 가능합니다.")
    private String question;
}
