package com.deskit.deskit.cart.entity;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity // JPA 엔티티로 등록 (DB 테이블과 매핑됨)
@Table(
        name = "cart", // 매핑할 테이블명: cart
        uniqueConstraints = {
                // 테이블 레벨 유니크 제약: member_id는 cart에서 유일해야 함(회원 1명당 장바구니 1개)
                @UniqueConstraint(name = "uk_cart_member", columnNames = "member_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// Lombok: JPA 스펙상 기본 생성자 필요
// 외부에서 무분별하게 new Cart() 못하게 protected로 제한
public class Cart extends BaseEntity {
    // BaseEntity에서 createdAt/updatedAt/deletedAt 같은 공통 컬럼을 상속받는 구조(프로젝트 기준)

    @Id // PK 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // MySQL AUTO_INCREMENT와 맞추는 전략
    @Column(name = "cart_id", nullable = false)
    // DB 컬럼명 cart_id로 매핑
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    // Member <-> Cart를 1:1로 맵핑
    // LAZY: cart를 가져올 때 member는 실제로 접근할 때 로딩(성능/불필요 조회 방지)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    // cart 테이블의 member_id 컬럼이 FK 역할
    // nullable=false: 반드시 회원이 있어야 장바구니 생성 가능
    // unique=true: member_id 중복 방지(회원 1명당 1개)
    private Member member;

    public Cart(Member member) {
        // 장바구니는 반드시 회원과 함께 생성되는 도메인 규칙을 강제
        this.member = member;
    }
}