package com.deskit.deskit.account.service;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.SellerRole;
import com.deskit.deskit.account.enums.SellerStatus;
import com.deskit.deskit.account.repository.SellerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerWithdrawService {

    private final SellerRepository sellerRepository;

    public void withdraw(String loginId) {
        Seller seller = sellerRepository.findByLoginId(loginId);
        if (seller == null) {
            throw new EntityNotFoundException("seller not found");
        }
        if (seller.getRole() == SellerRole.ROLE_SELLER_OWNER) {
            throw new AccessDeniedException("OWNER 권한은 탈퇴할 수 없습니다.");
        }
        if (seller.getStatus() == SellerStatus.INACTIVE) {
            throw new IllegalStateException("이미 탈퇴된 판매자입니다.");
        }

        seller.setStatus(SellerStatus.INACTIVE);
    }
}
