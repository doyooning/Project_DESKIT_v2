package com.deskit.deskit.livehost.service;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.livehost.common.enums.ActorType;
import com.deskit.deskit.livehost.common.enums.SanctionType;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode;
import com.deskit.deskit.livehost.dto.request.SanctionRequest;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.entity.Sanction;
import com.deskit.deskit.livehost.repository.BroadcastRepository;
import com.deskit.deskit.livehost.repository.SanctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SanctionService {

    private final SanctionRepository sanctionRepository;
    private final BroadcastRepository broadcastRepository;
    private final MemberRepository memberRepository;
    private final RedisService redisService;
    private final SseService sseService;
    private final OpenViduService openViduService;

    @Transactional
    public void sanctionUser(Long sellerId, Long broadcastId, SanctionRequest request) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (!broadcast.getSeller().getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        if (request.getActorType() != ActorType.SELLER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Member member = resolveMember(request);

        Sanction sanction = Sanction.builder()
                .broadcast(broadcast)
                .member(member)
                .actorType(request.getActorType())
                .sellerId(sellerId)
                .status(request.getStatus())
                .sanctionReason(request.getReason())
                .build();

        sanctionRepository.save(sanction);

        redisService.increment(redisService.getSanctionKey(broadcastId));

        if (request.getStatus() == SanctionType.OUT && request.getConnectionId() != null) {
            openViduService.forceDisconnect(broadcastId, request.getConnectionId());
        }

        sseService.notifyTargetUser(
                broadcastId,
                member.getMemberId(),
                "SANCTION_ALERT",
                Map.of(
                        "type", request.getStatus(),
                        "reason", request.getReason(),
                        "actorType", request.getActorType()
                )
        );
        sseService.notifyTargetUser(
                broadcastId,
                member.getLoginId(),
                "SANCTION_ALERT",
                Map.of(
                        "type", request.getStatus(),
                        "reason", request.getReason(),
                        "actorType", request.getActorType()
                )
        );

        sseService.notifyBroadcastUpdate(
                broadcastId,
                "SANCTION_UPDATED",
                Map.of("targetMemberId", member.getMemberId())
        );
    }

    @Transactional
    public void sanctionUserByAdmin(Long adminId, Long broadcastId, SanctionRequest request) {
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        if (request.getActorType() != ActorType.ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Member member = resolveMember(request);

        Sanction sanction = Sanction.builder()
                .broadcast(broadcast)
                .member(member)
                .actorType(request.getActorType())
                .adminId(adminId)
                .status(request.getStatus())
                .sanctionReason(request.getReason())
                .build();

        sanctionRepository.save(sanction);

        redisService.increment(redisService.getSanctionKey(broadcastId));

        if (request.getStatus() == SanctionType.OUT && request.getConnectionId() != null) {
            openViduService.forceDisconnect(broadcastId, request.getConnectionId());
        }

        sseService.notifyTargetUser(
                broadcastId,
                member.getMemberId(),
                "SANCTION_ALERT",
                Map.of(
                        "type", request.getStatus(),
                        "reason", request.getReason(),
                        "actorType", request.getActorType()
                )
        );
        sseService.notifyTargetUser(
                broadcastId,
                member.getLoginId(),
                "SANCTION_ALERT",
                Map.of(
                        "type", request.getStatus(),
                        "reason", request.getReason(),
                        "actorType", request.getActorType()
                )
        );

        sseService.notifyBroadcastUpdate(
                broadcastId,
                "SANCTION_UPDATED",
                Map.of("targetMemberId", member.getMemberId())
        );
    }

    private Member resolveMember(SanctionRequest request) {
        if (request.getMemberId() != null) {
            return memberRepository.findById(request.getMemberId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        }
        String loginId = request.getMemberLoginId();
        if (loginId == null || loginId.isBlank()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        Member member = memberRepository.findByLoginId(loginId);
        if (member == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }
}
