package com.deskit.deskit.account.dto;

import java.util.List;

public class SellerMyPageResponse {
	private final String companyName;
	private final String companyGrade;
	private final String gradeExpiredAt;
	private final List<SellerManagerStatusResponse> managers;

	public SellerMyPageResponse(
			String companyName,
			String companyGrade,
			String gradeExpiredAt,
			List<SellerManagerStatusResponse> managers
	) {
		this.companyName = companyName;
		this.companyGrade = companyGrade;
		this.gradeExpiredAt = gradeExpiredAt;
		this.managers = managers;
	}

	public String getCompanyName() {
		return companyName;
	}

	public String getCompanyGrade() {
		return companyGrade;
	}

	public String getGradeExpiredAt() {
		return gradeExpiredAt;
	}

	public List<SellerManagerStatusResponse> getManagers() {
		return managers;
	}
}
