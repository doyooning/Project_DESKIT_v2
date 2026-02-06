package com.deskit.deskit.account.entity;

import com.deskit.deskit.account.enums.SellerRole;
import com.deskit.deskit.account.enums.SellerStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "seller")
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SellerStatus status;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "login_id", nullable = false)
    private String loginId;

    @Lob
    @Column(name = "profile", columnDefinition = "TEXT")
    private String profile;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private SellerRole role;

    @Column(name = "is_agreed", nullable = false)
    private boolean isAgreed;
}
