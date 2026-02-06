package com.deskit.deskit.livehost.dto.request;

import com.deskit.deskit.livehost.common.enums.ActorType;
import com.deskit.deskit.livehost.common.enums.SanctionType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SanctionRequest {

    private Long memberId;

    private String memberLoginId;

    @NotNull
    private ActorType actorType;

    @NotNull(message = "제재 유형은 필수입니다.")
    private SanctionType status;

    private String reason;

    private String connectionId; // OpenVidu 연결 ID (연결을 끊기 위해 강퇴 시 필요)
}
