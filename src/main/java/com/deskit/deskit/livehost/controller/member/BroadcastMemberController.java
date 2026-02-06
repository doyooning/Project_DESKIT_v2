package com.deskit.deskit.livehost.controller.member;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.livehost.common.exception.ApiResult;
import com.deskit.deskit.livehost.common.utils.LiveAuthUtils;
import com.deskit.deskit.livehost.dto.response.BroadcastLikeResponse;
import com.deskit.deskit.livehost.dto.response.BroadcastReportResponse;
import com.deskit.deskit.livehost.service.BroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member/broadcasts")
@RequiredArgsConstructor
public class BroadcastMemberController {

    private final BroadcastService broadcastService;
    private final LiveAuthUtils liveAuthUtils;

    @PostMapping("/{broadcastId}/report")
    public ResponseEntity<ApiResult<BroadcastReportResponse>> reportBroadcast(
            @PathVariable Long broadcastId
    ) {
        Member member = liveAuthUtils.getCurrentMember();
        BroadcastReportResponse response = broadcastService.reportBroadcast(broadcastId, member.getMemberId());
        return ResponseEntity.ok(ApiResult.success(response));
    }

    @PostMapping("/{broadcastId}/like")
    public ResponseEntity<ApiResult<BroadcastLikeResponse>> likeBroadcast(
            @PathVariable Long broadcastId
    ) {
        Member member = liveAuthUtils.getCurrentMember();
        BroadcastLikeResponse response = broadcastService.likeBroadcast(broadcastId, member.getMemberId());
        return ResponseEntity.ok(ApiResult.success(response));
    }

    @GetMapping("/{broadcastId}/like-status")
    public ResponseEntity<ApiResult<BroadcastLikeResponse>> getLikeStatus(
            @PathVariable Long broadcastId
    ) {
        Member member = liveAuthUtils.getCurrentMember();
        BroadcastLikeResponse response = broadcastService.getBroadcastLikeStatus(broadcastId, member.getMemberId());
        return ResponseEntity.ok(ApiResult.success(response));
    }
}
