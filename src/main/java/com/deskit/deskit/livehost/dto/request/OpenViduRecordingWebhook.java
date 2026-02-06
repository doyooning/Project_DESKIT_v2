package com.deskit.deskit.livehost.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OpenViduRecordingWebhook {
    // 이벤트 타입 (recordingStatusChanged)
    private String event;

    // 녹화 ID
    private String id;

    // 방송 세션 ID (DB의 streamKey와 매핑)
    private String sessionId;

    // 녹화된 파일 이름
    private String name;

    // 파일 크기 (Byte)
    private Long size;

    // 녹화 시간 (초 단위, double로 올 수도 있어 확인 필요)
    private Double duration;

    // 녹화 상태 (stopped, ready, failed)
    private String status;

    // NCP Object Storage에 저장된 최종 URL
    private String url;
    // (참고) OpenVidu 버전에 따라 resolution, hasAudio 등이 올 수 있음
}
