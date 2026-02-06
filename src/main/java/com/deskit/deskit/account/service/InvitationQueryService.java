package com.deskit.deskit.account.service;

import com.deskit.deskit.account.dto.SellerManagerResponse;
import com.deskit.deskit.account.entity.Invitation;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.InvitationStatus;
import com.deskit.deskit.account.repository.InvitationRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvitationQueryService {

	private final InvitationRepository invitationRepository;
	private final SellerRepository sellerRepository;

	public List<SellerManagerResponse> findManagersForOwner(Long ownerSellerId) {
		List<Invitation> invitations = invitationRepository.findBySellerIdAndStatusIn(
				ownerSellerId,
				List.of(InvitationStatus.ACCEPTED.name())
		);

		if (invitations.isEmpty()) {
			return List.of();
		}

		List<SellerManagerResponse> managers = new ArrayList<>();
		for (Invitation invitation : invitations) {
			String email = invitation.getEmail();
			Seller seller = email == null ? null : sellerRepository.findByLoginId(email);
			if (seller == null) {
				continue;
			}

			String role = seller.getRole() == null ? "" : seller.getRole().name();
			managers.add(new SellerManagerResponse(
					seller.getSellerId(),
					seller.getName(),
					seller.getLoginId(),
					role
			));
		}

		return managers;
	}
}
