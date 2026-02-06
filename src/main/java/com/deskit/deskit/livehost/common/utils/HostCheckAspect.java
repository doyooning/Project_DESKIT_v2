package com.deskit.deskit.livehost.common.utils;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode;
import com.deskit.deskit.livehost.entity.Broadcast;
import com.deskit.deskit.livehost.repository.BroadcastRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class HostCheckAspect {
    private final BroadcastRepository broadcastRepository;
    private final LiveAuthUtils liveAuthUtils;

    @Before("@annotation(com.deskit.deskit.livehost.common.utils.HostCheck)")
    public void checkHost(JoinPoint joinPoint) {
        // 1. 현재 로그인한 판매자 정보 가져오기
        Seller seller = liveAuthUtils.getCurrentSeller();

        // 2. 파라미터에서 방송 ID(Long) 찾기
        Long broadcastId = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Long) {
                broadcastId = (Long) arg;
                break;
            }
        }

        if (broadcastId == null) {
            throw new BusinessException(ErrorCode.BROADCAST_NOT_FOUND);
        }

        // 3. 방송 조회
        Broadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));

        // 4. 권한 검증: 방송의 주인(Seller)과 현재 로그인한 Seller가 같은지 확인
        // Seller 엔티티의 ID getter 이름이 getSellerId()인지 getId()인지 확인 후 일치시켜 주세요.
        if (!broadcast.getSeller().getSellerId().equals(seller.getSellerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }
    }
}
