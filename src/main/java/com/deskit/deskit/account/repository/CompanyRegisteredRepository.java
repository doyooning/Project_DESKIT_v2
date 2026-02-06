package com.deskit.deskit.account.repository;

import com.deskit.deskit.account.entity.CompanyRegistered;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRegisteredRepository extends JpaRepository<CompanyRegistered, Long> {

	// Lookup for validating duplicate business numbers.
	CompanyRegistered findByBusinessNumber(String businessNumber);

	CompanyRegistered findBySellerId(Long sellerId);
}
