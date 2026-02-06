package com.deskit.deskit.tag.entity;

import com.deskit.deskit.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tag 엔티티
 * - tag 테이블과 매핑되는 도메인 엔티티
 * - BaseEntity를 상속하여 created_at / updated_at / deleted_at(소프트 삭제) 공통 컬럼을 함께 사용
 */
@Entity
@Table(name = "tag")
public class Tag extends BaseEntity {

  /**
   * PK (tag_id)
   * - MySQL AUTO_INCREMENT에 대응하기 위해 IDENTITY 전략 사용
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "tag_id", nullable = false)
  private Long id;

  /**
   * 태그 카테고리 연관관계 (N:1)
   * - tag.tag_category_id 컬럼으로 조인
   * - LAZY 로딩: 실제로 tagCategory를 사용할 때 조회(불필요한 조인 방지)
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_category_id", nullable = false)
  private TagCategory tagCategory;

  /**
   * 태그명 (tag_name)
   * - 스키마 VARCHAR(50) 제약에 맞춰 length 지정
   */
  @Column(name = "tag_name", nullable = false, length = 50)
  private String tagName;

  /**
   * JPA 기본 생성자 (필수)
   * - 외부에서 직접 사용하지 않도록 protected
   */
  protected Tag() {
  }

  /**
   * 태그 생성용 생성자
   * - tagCategory / tagName을 필수 값으로 받아 초기화
   */
  public Tag(TagCategory tagCategory, String tagName) {
    this.tagCategory = tagCategory;
    this.tagName = tagName;
  }

  public Long getId() {
    return id;
  }

  public TagCategory getTagCategory() {
    return tagCategory;
  }

  public String getTagName() {
    return tagName;
  }
}