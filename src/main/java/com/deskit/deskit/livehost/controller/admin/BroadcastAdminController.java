package com.deskit.deskit.livehost.controller.admin;

import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.admin.entity.Admin;
import com.deskit.deskit.admin.repository.AdminRepository;
import com.deskit.deskit.livehost.common.exception.ApiResult;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode;
import com.deskit.deskit.livehost.dto.request.BroadcastSearch;
import com.deskit.deskit.livehost.dto.request.SanctionRequest;
import com.deskit.deskit.livehost.dto.request.VodStatusRequest;
import com.deskit.deskit.livehost.dto.response.BroadcastResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastResultResponse;
import com.deskit.deskit.livehost.dto.response.SanctionStatisticsResponse;
import com.deskit.deskit.livehost.dto.response.StatisticsResponse;
import com.deskit.deskit.livehost.service.AdminService;
import com.deskit.deskit.livehost.service.BroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
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

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class BroadcastAdminController {
    private final AdminService adminService;
    private final BroadcastService broadcastService;
    private final AdminRepository adminRepository;

    @GetMapping("/broadcasts")
    public ResponseEntity<ApiResult<Object>> getAllBroadcasts(
            @ModelAttribute BroadcastSearch searchCondition,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getAdminBroadcasts(searchCondition, pageable)
        ));
    }

    @GetMapping("/broadcasts/{broadcastId}")
    public ResponseEntity<ApiResult<BroadcastResponse>> getBroadcastDetail(
            @PathVariable Long broadcastId
    ) {
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getAdminBroadcastDetail(broadcastId)
        ));
    }

    @PutMapping("/broadcasts/{broadcastId}/stop")
    public ResponseEntity<ApiResult<Void>> forceStop(
            @PathVariable Long broadcastId,
            @RequestBody Map<String, String> body
    ) {
        adminService.forceStopBroadcast(broadcastId, body.get("reason"));
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @PutMapping("/broadcasts/{broadcastId}/cancel")
    public ResponseEntity<ApiResult<Void>> cancelBroadcast(
            @PathVariable Long broadcastId,
            @RequestBody Map<String, String> body
    ) {
        adminService.cancelBroadcast(broadcastId, body.get("reason"));
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @PostMapping("/broadcasts/{broadcastId}/sanctions")
    public ResponseEntity<ApiResult<Void>> sanctionViewer(
            Authentication authentication,
            @PathVariable Long broadcastId,
            @RequestBody SanctionRequest request
    ) {
        Long adminId = resolveAdminId(authentication);
        adminService.sanctionViewer(adminId, broadcastId, request);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResult<StatisticsResponse>> getAdminStatistics(
            @RequestParam(defaultValue = "DAILY") String period
    ) {
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getStatistics(null, period)
        ));
    }

    @GetMapping("/sanctions/statistics")
    public ResponseEntity<ApiResult<SanctionStatisticsResponse>> getSanctionStatistics(
            @RequestParam(defaultValue = "DAILY") String period
    ) {
        return ResponseEntity.ok(ApiResult.success(
                adminService.getSanctionStatistics(period)
        ));
    }

    @GetMapping("/broadcasts/{broadcastId}/report")
    public ResponseEntity<ApiResult<BroadcastResultResponse>> getBroadcastReport(
            @PathVariable Long broadcastId
    ) {
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.getBroadcastResult(broadcastId, null, true)
        ));
    }

    @PutMapping("/broadcasts/{broadcastId}/vod/visibility")
    public ResponseEntity<ApiResult<String>> updateVodVisibility(
            @PathVariable Long broadcastId,
            @RequestBody VodStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResult.success(
                broadcastService.updateAdminVodVisibility(broadcastId, request.getStatus())
        ));
    }

    @DeleteMapping("/broadcasts/{broadcastId}/vod")
    public ResponseEntity<ApiResult<Void>> deleteVod(
            @PathVariable Long broadcastId
    ) {
        broadcastService.deleteAdminVod(broadcastId);
        return ResponseEntity.ok(ApiResult.success(null));
    }

    private Long resolveAdminId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        Object principal = authentication.getPrincipal();
        String loginId = null;
        if (principal instanceof CustomOAuth2User oauthUser) {
            loginId = oauthUser.getUsername();
        } else if (principal instanceof UserDetails userDetails) {
            loginId = userDetails.getUsername();
        } else if (principal != null) {
            loginId = principal.toString();
        }

        if (loginId == null || loginId.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        Admin admin = adminRepository.findByLoginId(loginId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        return admin.getAdminId();
    }
}
