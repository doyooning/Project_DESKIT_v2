package com.deskit.deskit.account.entity;

import com.deskit.deskit.account.enums.SellerGradeEnum;
import com.deskit.deskit.account.enums.SellerGradeStatus;
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
@Table(name = "seller_grade")
public class SellerGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_id")
    private Long gradeId;

    @Column(name = "grade")
    @Enumerated(EnumType.STRING)
    private SellerGradeEnum grade;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SellerGradeStatus gradeStatus;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "company_id")
    private Long companyId;
}
