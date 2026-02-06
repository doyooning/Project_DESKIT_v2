package com.deskit.deskit.livehost.controller.seller;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.livehost.common.enums.UploadType;
import com.deskit.deskit.livehost.common.exception.ApiResult;
import com.deskit.deskit.livehost.common.utils.LiveAuthUtils;
import com.deskit.deskit.livehost.dto.response.ImageUploadResponse;
import com.deskit.deskit.livehost.service.AwsS3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/seller/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final AwsS3Service awsS3Service;
    private final LiveAuthUtils liveAuthUtils;

    @PostMapping("/{type}")
    public ResponseEntity<ApiResult<ImageUploadResponse>> uploadImage(
            @PathVariable UploadType type,
            @RequestPart("file") MultipartFile file) {

        Seller seller = liveAuthUtils.getCurrentSeller();
        ImageUploadResponse response = awsS3Service.uploadFile(seller.getSellerId(), file, type);
        return ResponseEntity.ok(ApiResult.success(response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResult<String>> deleteImage(
            @RequestParam String fileName) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        awsS3Service.deleteFile(seller.getSellerId(), fileName);
        return ResponseEntity.ok(ApiResult.success("이미지가 삭제되었습니다."));
    }
}
