package com.deskit.deskit.livehost.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.deskit.deskit.livehost.common.enums.UploadType;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode;
import com.deskit.deskit.livehost.dto.response.ImageUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsS3Service {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.endpoint}")
    private String endpoint;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png");

    public ImageUploadResponse uploadFile(Long sellerId, MultipartFile file, UploadType type) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        if (file.getSize() > type.getMaxSizeBytes()) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        validateImageRatio(file, type);

        String folderPath = "seller_" + sellerId + "/" + type.name().toLowerCase();
        String storedFileName = folderPath + "/" + UUID.randomUUID() + "." + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        try (InputStream inputStream = file.getInputStream()) {
            amazonS3.putObject(new PutObjectRequest(bucket, storedFileName, inputStream, metadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead));

            return ImageUploadResponse.builder()
                    .originalFileName(originalFileName)
                    .storedFileName(storedFileName)
                    .fileUrl(amazonS3.getUrl(bucket, storedFileName).toString())
                    .fileSize(file.getSize())
                    .build();

        } catch (IOException e) {
            log.error("S3 업로드 에러", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public String buildPublicUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmedKey = key.startsWith("/") ? key.substring(1) : key;
        if (endpoint != null && !endpoint.isBlank()) {
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            return base + "/" + bucket + "/" + trimmedKey;
        }
        return amazonS3.getUrl(bucket, trimmedKey).toString();
    }

    public void deleteFile(Long sellerId, String storedFileName) {
        String expectedPrefix = "seller_" + sellerId + "/";
        if (!storedFileName.startsWith(expectedPrefix)) {
            log.warn("이미지 삭제 권한 없음: 요청자={}, 파일={}", sellerId, storedFileName);
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        try {
            if (amazonS3.doesObjectExist(bucket, storedFileName)) {
                amazonS3.deleteObject(bucket, storedFileName);
            }
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패: {}", storedFileName, e);
            throw new BusinessException(ErrorCode.FILE_DELETE_FAILED);
        }
    }

    public String uploadVodStream(InputStream inputStream, String pathKey, long contentLength) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            metadata.setContentType("video/mp4");

            amazonS3.putObject(new PutObjectRequest(bucket, pathKey, inputStream, metadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead));

            return endpoint != null ? endpoint + "/" + bucket + "/" + pathKey
                    : amazonS3.getUrl(bucket, pathKey).toString();
        } catch (Exception e) {
            log.error("S3 Stream Upload Failed: {}", e.getMessage());
            throw new RuntimeException("VOD 업로드 실패");
        }
    }

    public long getObjectSize(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);
        if (key == null) {
            return 0L;
        }
        try {
            ObjectMetadata metadata = amazonS3.getObjectMetadata(bucket, key);
            return metadata.getContentLength();
        } catch (Exception e) {
            log.warn("Failed to read object metadata for {}", fileUrl, e);
            return 0L;
        }
    }

    public InputStream getObjectStream(String fileUrl, Long start, Long end) {
        String key = extractKeyFromUrl(fileUrl);
        if (key == null) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        try {
            com.amazonaws.services.s3.model.GetObjectRequest request = new com.amazonaws.services.s3.model.GetObjectRequest(bucket, key);
            if (start != null && end != null) {
                request.setRange(start, end);
            }
            return amazonS3.getObject(request).getObjectContent();
        } catch (Exception e) {
            log.error("Failed to stream S3 object: {}", fileUrl, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public void deleteObjectByUrl(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);
        if (key == null) {
            return;
        }
        try {
            if (amazonS3.doesObjectExist(bucket, key)) {
                amazonS3.deleteObject(bucket, key);
            }
        } catch (Exception e) {
            log.error("Failed to delete S3 object: {}", fileUrl, e);
            throw new BusinessException(ErrorCode.FILE_DELETE_FAILED);
        }
    }

    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(fileUrl);
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            String trimmed = path.startsWith("/") ? path.substring(1) : path;
            if (trimmed.startsWith(bucket + "/")) {
                return trimmed.substring(bucket.length() + 1);
            }
            return trimmed;
        } catch (Exception e) {
            log.warn("Failed to parse S3 key from URL: {}", fileUrl, e);
            return null;
        }
    }

    private void validateImageRatio(MultipartFile file, UploadType type) {
        if (type.getWidthRatio() <= 0 || type.getHeightRatio() <= 0) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());

            if (image == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
            }

            double actualRatio = (double) image.getWidth() / image.getHeight();
            double targetRatio = type.getTargetRatio();

            if (Math.abs(actualRatio - targetRatio) > 0.05) {
                log.warn("이미지 비율 불일치: 기대={}, 실제={}", targetRatio, actualRatio);
                throw new BusinessException(ErrorCode.INVALID_IMAGE_RATIO);
            }

        } catch (IOException e) {
            log.error("이미지 읽기 및 검증 실패", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
