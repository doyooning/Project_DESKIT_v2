package com.deskit.deskit.product.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class S3Uploader {

  private final AmazonS3 amazonS3;

  @Value("${cloud.aws.s3.bucket}")
  private String bucket;

  @Value("${cloud.aws.s3.endpoint}")
  private String endpoint;

  public S3Uploader(AmazonS3 amazonS3) {
    this.amazonS3 = amazonS3;
  }

  @PostConstruct
  void validateConfig() {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("cloud.aws.s3.bucket is required");
    }
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalStateException("cloud.aws.s3.endpoint is required");
    }
  }

  public String upload(String keyPrefix, MultipartFile file) {
    String originalFileName = file.getOriginalFilename();
    String extension = resolveExtension(originalFileName);
    String objectKey = keyPrefix + "/" + UUID.randomUUID() + extension;

    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentType(file.getContentType());
    metadata.setContentLength(file.getSize());

    try (InputStream inputStream = file.getInputStream()) {
      amazonS3.putObject(bucket, objectKey, inputStream, metadata);
      return amazonS3.getUrl(bucket, objectKey).toString();
    } catch (Exception ex) {
      throw new IllegalStateException("file upload failed");
    }
  }

  private String resolveExtension(String originalFileName) {
    if (originalFileName == null || originalFileName.isBlank()) {
      return "";
    }
    int dotIndex = originalFileName.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == originalFileName.length() - 1) {
      return "";
    }
    return originalFileName.substring(dotIndex);
  }
}
