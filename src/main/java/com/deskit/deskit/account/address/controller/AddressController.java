package com.deskit.deskit.account.address.controller;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.address.dto.AddressCreateRequest;
import com.deskit.deskit.account.address.dto.AddressResponse;
import com.deskit.deskit.account.address.dto.AddressUpdateRequest;
import com.deskit.deskit.account.address.service.AddressService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

  private final AddressService addressService;
  private final MemberRepository memberRepository;

  public AddressController(AddressService addressService, MemberRepository memberRepository) {
    this.addressService = addressService;
    this.memberRepository = memberRepository;
  }

  @GetMapping
  public ResponseEntity<List<AddressResponse>> getMyAddresses(
      @AuthenticationPrincipal CustomOAuth2User user
  ) {
    Long memberId = resolveMemberId(user);
    return ResponseEntity.ok(addressService.getMyAddresses(memberId));
  }

  @PostMapping
  public ResponseEntity<AddressResponse> createAddress(
      @AuthenticationPrincipal CustomOAuth2User user,
      @Valid @RequestBody AddressCreateRequest request
  ) {
    Long memberId = resolveMemberId(user);
    return ResponseEntity.ok(addressService.createAddress(memberId, request));
  }

  @PatchMapping("/{addressId}")
  public ResponseEntity<AddressResponse> updateAddress(
      @AuthenticationPrincipal CustomOAuth2User user,
      @PathVariable("addressId") Long addressId,
      @Valid @RequestBody AddressUpdateRequest request
  ) {
    Long memberId = resolveMemberId(user);
    return ResponseEntity.ok(addressService.updateAddress(memberId, addressId, request));
  }

  @DeleteMapping("/{addressId}")
  public ResponseEntity<Void> deleteAddress(
      @AuthenticationPrincipal CustomOAuth2User user,
      @PathVariable("addressId") Long addressId
  ) {
    Long memberId = resolveMemberId(user);
    addressService.deleteAddress(memberId, addressId);
    return ResponseEntity.noContent().build();
  }

  private Long resolveMemberId(CustomOAuth2User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    Long memberId = tryExtractMemberId(user);
    if (memberId != null) {
      return memberId;
    }

    String loginId = user.getUsername();
    if (loginId == null || loginId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }

    Member member = memberRepository.findByLoginId(loginId);
    if (member == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }

    return member.getMemberId();
  }

  private Long tryExtractMemberId(CustomOAuth2User user) {
    Map<String, Object> attributes = user.getAttributes();
    if (attributes == null || attributes.isEmpty()) {
      return null;
    }

    Object value = attributes.get("memberId");
    if (value == null) {
      value = attributes.get("member_id");
    }
    if (value == null) {
      value = attributes.get("id");
    }

    if (value instanceof Number) {
      return ((Number) value).longValue();
    }

    if (value instanceof String) {
      String text = ((String) value).trim();
      if (text.isEmpty()) {
        return null;
      }
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ex) {
        return null;
      }
    }

    return null;
  }
}
