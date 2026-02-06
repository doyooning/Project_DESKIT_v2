package com.deskit.deskit.livehost.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MediaConfigResponse {
    private String cameraId;
    private String microphoneId;
    private boolean cameraOn;
    private boolean microphoneOn;
    private int volume;
}
