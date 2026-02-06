package com.deskit.deskit.livehost.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageUploadResponse {
    private String originalFileName; // 사용자가 올린 원래 파일명 (예: cat.jpg)
    private String storedFileName;   // 서버(S3)에 저장된 파일명 (예: uuid-cat.jpg)
    private String fileUrl;          // 실제 접근 가능한 이미지 주소 (https://s3...)
    private Long fileSize;           // 파일 용량 (Byte 단위)
}

