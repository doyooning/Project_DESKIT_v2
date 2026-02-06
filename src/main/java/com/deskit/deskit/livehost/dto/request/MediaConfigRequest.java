package com.deskit.deskit.livehost.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MediaConfigRequest {

    @NotBlank(message = "카메라 ID는 필수입니다.")
    private String cameraId;

    @NotBlank(message = "마이크 ID는 필수입니다.")
    private String microphoneId;

    @NotNull(message = "카메라 상태는 필수입니다.")
    private Boolean cameraOn;

    @NotNull(message = "마이크 상태는 필수입니다.")
    private Boolean microphoneOn;

    @Min(value = 0, message = "볼륨은 0 이상이어야 합니다.")
    @Max(value = 100, message = "볼륨은 100 이하이어야 합니다.")
    private int volume;
}
