package com.deskit.deskit.account.entity;

import com.deskit.deskit.account.enums.CompanyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "company_registered")
public class CompanyRegistered {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "business_number", nullable = false)
    private String businessNumber;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private CompanyStatus companyStatus;
}
