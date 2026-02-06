package com.deskit.deskit.account.address.entity;

import com.deskit.deskit.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "address_id", nullable = false)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "receiver", nullable = false, length = 20)
  private String receiver;

  @Column(name = "postcode", nullable = false, length = 10)
  private String postcode;

  @Column(name = "addr_detail", nullable = false, length = 255)
  private String addrDetail;

  @Column(name = "is_default", nullable = false)
  private Boolean isDefault;

  public static Address create(
      Long memberId,
      String receiver,
      String postcode,
      String addrDetail,
      boolean isDefault
  ) {
    Address address = new Address();
    address.memberId = memberId;
    address.receiver = receiver;
    address.postcode = postcode;
    address.addrDetail = addrDetail;
    address.isDefault = isDefault;
    return address;
  }

  public void markDefault(boolean value) {
    this.isDefault = value;
  }

  public void update(String receiver, String postcode, String addrDetail) {
    this.receiver = receiver;
    this.postcode = postcode;
    this.addrDetail = addrDetail;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }
}
