package com.deskit.deskit.setup.entity;

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
@Table(name = "setup")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setup extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "setup_id", nullable = false)
  private Long id;

  @Column(name = "seller_id", nullable = false)
  private Long sellerId;

  @Column(name = "setup_name", nullable = false, length = 100)
  private String setupName;

  @Column(name = "short_desc", nullable = false, length = 250)
  private String shortDesc;

  @Column(name = "tip_text", length = 500)
  private String tipText;

  @Column(name = "setup_image_url", nullable = false, length = 500)
  private String setupImageUrl;

  public Setup(Long sellerId, String setupName, String shortDesc, String tipText,
               String setupImageUrl) {
    this.sellerId = sellerId;
    this.setupName = setupName;
    this.shortDesc = shortDesc;
    this.tipText = tipText;
    this.setupImageUrl = setupImageUrl;
  }
}
