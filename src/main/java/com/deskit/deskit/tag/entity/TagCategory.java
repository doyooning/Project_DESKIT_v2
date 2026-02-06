package com.deskit.deskit.tag.entity;

import com.deskit.deskit.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * TagCategory 엔티티
 * - tag_category 테이블과 매핑되는 도메인 엔티티
 * - BaseEntity를 상속하여 created_at / updated_at / deleted_at(소프트 삭제) 공통 컬럼을 함께 사용
 */
@Entity
@Table(name = "tag_category")
public class TagCategory extends BaseEntity {

  /**
   * 태그 카테고리 분류 코드 (tag_code)
   * - DB ENUM('SPACE','TONE','SITUATION','MOOD')와 매핑
   * - EnumType.STRING: enum 이름(SPACE 등)을 문자열로 저장/조회
   */
  public enum TagCode {
    SPACE,
    TONE,
    SITUATION,
    MOOD
  }

  /**
   * PK (tag_category_id)
   * - MySQL AUTO_INCREMENT에 대응하기 위해 IDENTITY 전략 사용
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "tag_category_id", nullable = false)
  private Long id;

  /**
   * 카테고리 코드 (tag_code)
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "tag_code", nullable = false)
  private TagCode tagCode;

  /**
   * 카테고리명 (tag_category_name)
   * - 스키마 VARCHAR(30) 제약에 맞춰 length 지정
   */
  @Column(name = "tag_category_name", nullable = false, length = 30)
  private String tagCategoryName;

  /**
   * JPA 기본 생성자 (필수)
   * - 외부에서 직접 사용하지 않도록 protected
   */
  protected TagCategory() {
  }

  /**
   * 카테고리 생성용 생성자
   * - tagCode / tagCategoryName을 필수 값으로 받아 초기화
   */
  public TagCategory(TagCode tagCode, String tagCategoryName) {
    this.tagCode = tagCode;
    this.tagCategoryName = tagCategoryName;
  }

  public Long getId() {
    return id;
  }

  public TagCode getTagCode() {
    return tagCode;
  }

  public String getTagCategoryName() {
    return tagCategoryName;
  }
}