package com.deskit.deskit.account.address.service;

import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.address.dto.AddressCreateRequest;
import com.deskit.deskit.account.address.dto.AddressResponse;
import com.deskit.deskit.account.address.dto.AddressUpdateRequest;
import com.deskit.deskit.account.address.entity.Address;
import com.deskit.deskit.account.address.repository.AddressRepository;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@RequiredArgsConstructor
public class AddressService {

  private static final int MAX_ADDRESS_COUNT = 3;
  private static final Pattern POSTCODE_PATTERN = Pattern.compile("\\d{5}");

  private final AddressRepository addressRepository;
  private final MemberRepository memberRepository;

  @Transactional(readOnly = true)
  public List<AddressResponse> getMyAddresses(Long memberId) {
    ensureMemberExists(memberId);
    return addressRepository.findByMemberIdOrderByIsDefaultDescIdDesc(memberId)
      .stream()
      .map(AddressResponse::from)
      .collect(Collectors.toList());
  }

  public AddressResponse createAddress(Long memberId, AddressCreateRequest request) {
    ensureMemberExists(memberId);
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
    }

    String receiver = normalizeReceiver(request.receiver());
    String postcode = normalizePostcode(request.postcode());
    String addrDetail = normalizeAddrDetail(request.addrDetail());

    long count = addressRepository.countByMemberId(memberId);
    if (count >= MAX_ADDRESS_COUNT) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "address limit exceeded");
    }

    boolean requestedDefault = Boolean.TRUE.equals(request.isDefault());
    boolean isDefault = requestedDefault || count == 0;
    if (isDefault) {
      addressRepository.clearDefaultByMemberId(memberId);
    }

    Address address = Address.create(memberId, receiver, postcode, addrDetail, isDefault);
    return AddressResponse.from(addressRepository.save(address));
  }

  public AddressResponse updateAddress(Long memberId, Long addressId, AddressUpdateRequest request) {
    ensureMemberExists(memberId);
    if (addressId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "address_id required");
    }
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
    }

    Address address = addressRepository.findByIdAndMemberId(addressId, memberId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "address not found"));

    String receiver = normalizeReceiver(request.receiver());
    String postcode = normalizePostcode(request.postcode());
    String addrDetail = normalizeAddrDetail(request.addrDetail());

    boolean requestedDefault = Boolean.TRUE.equals(request.isDefault());
    if (requestedDefault) {
      addressRepository.clearDefaultByMemberId(memberId);
    }

    address.markDefault(requestedDefault);
    address.update(receiver, postcode, addrDetail);
    return AddressResponse.from(addressRepository.save(address));
  }

  public void deleteAddress(Long memberId, Long addressId) {
    ensureMemberExists(memberId);
    if (addressId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "address_id required");
    }
    Address address = addressRepository.findByIdAndMemberId(addressId, memberId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "address not found"));
    addressRepository.delete(address);
  }

  public void saveAddressFromOrder(
    Long memberId,
    String receiver,
    String postcode,
    String addrDetail,
    Boolean saveAsDefault
  ) {
    if (memberId == null) {
      return;
    }

    String normalizedReceiver = normalizeReceiver(receiver);
    String normalizedPostcode = normalizePostcode(postcode);
    String normalizedAddrDetail = normalizeAddrDetail(addrDetail);

    long count = addressRepository.countByMemberId(memberId);
    boolean shouldSave = Boolean.TRUE.equals(saveAsDefault) || count == 0;
    if (!shouldSave) {
      return;
    }
    if (count >= MAX_ADDRESS_COUNT) {
      return;
    }

    boolean requestedDefault = Boolean.TRUE.equals(saveAsDefault);
    boolean isDefault = requestedDefault || count == 0;
    if (isDefault) {
      addressRepository.clearDefaultByMemberId(memberId);
    }

    Address address = Address.create(
      memberId,
      normalizedReceiver,
      normalizedPostcode,
      normalizedAddrDetail,
      isDefault
    );
    addressRepository.save(address);
  }

  private void ensureMemberExists(Long memberId) {
    if (memberId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member_id required");
    }
    if (!memberRepository.existsById(memberId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
    }
  }

  private String normalizeReceiver(String receiver) {
    String normalized = receiver == null ? "" : receiver.trim();
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiver required");
    }
    return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
  }

  private String normalizePostcode(String postcode) {
    String normalized = postcode == null ? "" : postcode.trim();
    if (!POSTCODE_PATTERN.matcher(normalized).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "postcode invalid");
    }
    return normalized;
  }

  private String normalizeAddrDetail(String addrDetail) {
    String normalized = addrDetail == null ? "" : addrDetail.trim();
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addr_detail required");
    }
    return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
  }
}
