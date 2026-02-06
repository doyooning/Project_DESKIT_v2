package com.deskit.deskit.livehost.controller.seller;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.livehost.common.exception.ApiResult;
import com.deskit.deskit.livehost.common.utils.LiveAuthUtils;
import com.deskit.deskit.livehost.dto.request.BroadcastCreateRequest;
import com.deskit.deskit.livehost.dto.request.BroadcastSearch;
import com.deskit.deskit.livehost.dto.request.BroadcastUpdateRequest;
import com.deskit.deskit.livehost.dto.request.MediaConfigRequest;
import com.deskit.deskit.livehost.dto.request.SanctionRequest;
import com.deskit.deskit.livehost.dto.request.VodStatusRequest;
import com.deskit.deskit.livehost.dto.response.BroadcastResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastResultResponse;
import com.deskit.deskit.livehost.dto.response.MediaConfigResponse;
import com.deskit.deskit.livehost.dto.response.ProductSelectResponse;
import com.deskit.deskit.livehost.dto.response.ReservationSlotResponse;
import com.deskit.deskit.livehost.dto.response.StatisticsResponse;
import com.deskit.deskit.livehost.service.BroadcastService;
import com.deskit.deskit.livehost.service.SanctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/seller/broadcasts")
@RequiredArgsConstructor
public class BroadcastSellerController {

    private final BroadcastService broadcastService;
    private final SanctionService sanctionService;
    private final LiveAuthUtils liveAuthUtils;

    @PostMapping
    public ResponseEntity<ApiResult<Long>> createBroadcast(
            @RequestBody @Valid BroadcastCreateRequest request
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.createBroadcast(seller.getSellerId(), request)
        ));
    }

    @PutMapping("/{broadcastId}")
    public ResponseEntity<ApiResult<Long>> updateBroadcast(
            @PathVariable Long broadcastId,
            @RequestBody @Valid BroadcastUpdateRequest request
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.updateBroadcast(seller.getSellerId(), broadcastId, request)
        ));
    }

    @DeleteMapping("/{broadcastId}")
    public ResponseEntity<ApiResult<Void>> cancelBroadcast(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        broadcastService.cancelBroadcast(seller.getSellerId(), broadcastId);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResult<List<ProductSelectResponse>>> getSellerProducts(
            @RequestParam(required = false) String keyword
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getSellerProducts(seller.getSellerId(), keyword)
        ));
    }

    @GetMapping("/reservation-slots")
    public ResponseEntity<ApiResult<List<ReservationSlotResponse>>> getReservationSlots(
            @RequestParam("date") String date
    ) {
        LocalDate targetDate = LocalDate.parse(date);
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getReservableSlots(targetDate)
        ));
    }

    @PutMapping("/{broadcastId}/media-config")
    public ResponseEntity<ApiResult<Void>> saveMediaConfig(
            @PathVariable Long broadcastId,
            @RequestBody @Valid MediaConfigRequest request
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        broadcastService.saveMediaConfig(seller.getSellerId(), broadcastId, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @GetMapping("/{broadcastId}/media-config")
    public ResponseEntity<ApiResult<MediaConfigResponse>> getMediaConfig(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getMediaConfig(seller.getSellerId(), broadcastId)
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResult<Object>> getSellerBroadcasts(
            @ModelAttribute BroadcastSearch searchCondition,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getSellerBroadcasts(seller.getSellerId(), searchCondition, pageable)
        ));
    }

    @GetMapping("/{broadcastId}")
    public ResponseEntity<ApiResult<BroadcastResponse>> getBroadcastDetail(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getBroadcastDetail(seller.getSellerId(), broadcastId)
        ));
    }

    @PostMapping("/{broadcastId}/start")
    public ResponseEntity<ApiResult<String>> startBroadcast(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        String token = broadcastService.startBroadcast(seller.getSellerId(), broadcastId);
        return ResponseEntity.ok(ApiResult.success(token));
    }

    @PostMapping("/{broadcastId}/recording/start")
    public ResponseEntity<ApiResult<Void>> startRecording(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        broadcastService.startRecording(seller.getSellerId(), broadcastId);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @PostMapping("/{broadcastId}/end")
    public ResponseEntity<ApiResult<Void>> endBroadcast(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        broadcastService.endBroadcast(seller.getSellerId(), broadcastId);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @PostMapping("/{broadcastId}/pin/{productId}")
    public ResponseEntity<ApiResult<Void>> pinProduct(
            @PathVariable Long broadcastId,
            @PathVariable("productId") Long bpId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        broadcastService.pinProduct(seller.getSellerId(), broadcastId, bpId);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @DeleteMapping("/{broadcastId}/pin")
    public ResponseEntity<ApiResult<Void>> unpinProduct(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        broadcastService.unpinProduct(seller.getSellerId(), broadcastId);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @PostMapping("/{broadcastId}/sanctions")
    public ResponseEntity<ApiResult<Void>> sanctionUser(
            @PathVariable Long broadcastId,
            @RequestBody SanctionRequest request) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        sanctionService.sanctionUser(seller.getSellerId(), broadcastId, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResult<StatisticsResponse>> getStatistics(
            @RequestParam(defaultValue = "DAILY") String period
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getStatistics(seller.getSellerId(), period)
        ));
    }

    @GetMapping("/{broadcastId}/report")
    public ResponseEntity<ApiResult<BroadcastResultResponse>> getBroadcastResultReport(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getBroadcastResult(broadcastId, seller.getSellerId(), false)
        ));
    }

    @PutMapping("/{broadcastId}/vod/visibility")
    public ResponseEntity<ApiResult<String>> updateVodVisibility(
            @PathVariable Long broadcastId,
            @RequestBody @Valid VodStatusRequest request
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.updateVodVisibility(seller.getSellerId(), broadcastId, request.getStatus())
        ));
    }

    @DeleteMapping("/{broadcastId}/vod")
    public ResponseEntity<ApiResult<Void>> deleteVod(
            @PathVariable Long broadcastId
    ) {
        Seller seller = liveAuthUtils.getCurrentSeller();
        broadcastService.deleteVod(seller.getSellerId(), broadcastId);
        return ResponseEntity.ok(ApiResult.success(null));
    }
}
