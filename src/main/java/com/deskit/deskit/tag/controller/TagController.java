package com.deskit.deskit.tag.controller;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.SellerStatus;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.tag.dto.TagListResponse;
import com.deskit.deskit.tag.repository.TagRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/seller/tags")
public class TagController {

  private final TagRepository tagRepository;
  private final SellerRepository sellerRepository;

  public TagController(TagRepository tagRepository,
                       SellerRepository sellerRepository) {
    this.tagRepository = tagRepository;
    this.sellerRepository = sellerRepository;
  }

  @GetMapping
  public ResponseEntity<List<TagListResponse>> listTags(
          @AuthenticationPrincipal CustomOAuth2User user
  ) {
    validateActiveSeller(user);
    List<TagListResponse> responses = tagRepository.findActiveTagResponsesOrderByCategoryAndName();
    return ResponseEntity.ok(responses);
  }

  private void validateActiveSeller(CustomOAuth2User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }

    boolean isSeller = user.getAuthorities() != null
      && user.getAuthorities().stream()
        .filter(Objects::nonNull)
        .map(GrantedAuthority::getAuthority)
        .filter(Objects::nonNull)
        .anyMatch(role -> role.startsWith("ROLE_SELLER"));
    if (!isSeller) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller role required");
    }

    String loginId = user.getUsername();
    if (loginId == null || loginId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller not found");
    }

    Seller seller = sellerRepository.findByLoginId(loginId);
    if (seller == null || seller.getSellerId() == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller not found");
    }
    if (seller.getStatus() != SellerStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "seller inactive");
    }
  }
}
