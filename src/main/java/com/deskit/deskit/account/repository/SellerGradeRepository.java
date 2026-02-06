package com.deskit.deskit.account.repository;

import com.deskit.deskit.account.entity.SellerGrade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerGradeRepository extends JpaRepository<SellerGrade, Long> {
	SellerGrade findByCompanyId(Long companyId);
}
