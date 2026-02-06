package com.deskit.deskit.account.repository;

import com.deskit.deskit.account.entity.Seller;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    // Lookup seller by social login id.
    Seller findByLoginId(String loginId);
}
