package com.deskit.deskit.common.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * 공통 엔티티 베이스 클래스(BaseEntity)
 *
 * 역할
 * - 여러 도메인 엔티티(Product, Tag, Order 등)에서 공통으로 사용하는 "시간 컬럼 + 소프트 삭제" 필드를 제공한다.
 * - 각 엔티티는 이 클래스를 상속(extends)함으로써 created_at/updated_at/deleted_at 컬럼을 일관되게 갖게 된다.
 *
 * 기능
 * - createdAt: 최초 생성 시각(INSERT 시 자동 세팅)
 * - updatedAt: 마지막 수정 시각(INSERT/UPDATE 시 자동 세팅)
 * - deletedAt: 소프트 삭제 시각(null이면 활성 상태, 값이 있으면 삭제된 것으로 간주)
 *
 * 동작 방식(JPA Lifecycle Callback)
 * - @PrePersist: 엔티티가 처음 저장되기 직전에 호출되어 createdAt/updatedAt을 현재 시각으로 세팅한다.
 * - @PreUpdate: 엔티티가 수정되기 직전에 호출되어 updatedAt을 현재 시각으로 갱신한다.
 *
 * 주의사항
 * - deletedAt은 "소프트 삭제"를 위한 값일 뿐, 이 값이 있다고 자동으로 조회에서 제외되지는 않는다.
 *   (즉, 조회 시 deletedAt IS NULL 조건을 리포지토리/쿼리 레벨에서 적용해야 한다.)
 * - createdAt은 updatable=false로 설정되어 이후 업데이트에서 변경되지 않는다.
 */
@MappedSuperclass
public abstract class BaseEntity {

  // 엔티티 최초 생성 시각 (created_at)
  // - updatable=false: 한번 생성되면 이후 업데이트 시 변경되지 않도록 고정
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  // 엔티티 마지막 수정 시각 (updated_at)
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // 소프트 삭제 시각 (deleted_at)
  // - null: 활성 상태
  // - not null: 삭제된 상태로 간주(조회 필터는 별도 적용 필요)
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /**
   * 최초 저장 직전 호출
   * - createdAt/updatedAt이 비어있으면 현재 시각으로 채운다.
   */
  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  /**
   * 업데이트 직전 호출
   * - updatedAt을 현재 시각으로 갱신한다.
   */
  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  /**
   * 소프트 삭제용 세터
   * - 삭제 처리 시 deletedAt에 현재 시각을 넣는다.
   * - 복구 처리 시 deletedAt을 null로 되돌릴 수 있다(정책에 따라 허용/금지 결정).
   */
  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }
}