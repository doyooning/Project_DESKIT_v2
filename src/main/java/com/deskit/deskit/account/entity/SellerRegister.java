package com.deskit.deskit.account.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "seller_register")
public class SellerRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "register_id")
    private Long registerId;

    @Lob
    @Column(name = "plan_file", columnDefinition = "LONGBLOB")
    private byte[] planFile;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "description")
    private String description;

    @Column(name = "company_name")
    private String companyName;
}
