package com.deskit.deskit.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "admin")
public class Admin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "name", nullable = false)
    private String name;

    // Email과 동일
    @Column(name = "login_id", nullable = false)
    private String loginId;

    @Column(name = "phone", nullable = false)
    private String phone;

    // ROLE_ADMIN 고정
    @Column(name = "role", nullable = false)
    private String role;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
