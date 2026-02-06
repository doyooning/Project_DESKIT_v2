package com.deskit.deskit.admin.service;

import com.deskit.deskit.admin.dto.AdminCompanyPageResponse;
import com.deskit.deskit.admin.dto.AdminCompanyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCompanyService {

	private final JdbcTemplate jdbcTemplate;

	public AdminCompanyPageResponse listCompanies(
			int page,
			int size,
			String keyword,
			String companyName,
			String businessNumber,
			String grade,
			String status,
			String fromDate,
			String toDate
	) {
		int normalizedPage = Math.max(page, 0);
		int normalizedSize = Math.max(size, 1);
		int offset = normalizedPage * normalizedSize;

		String baseQuery = """
				FROM (
					SELECT cr.company_id,
					       company_name,
					       business_number,
					       COALESCE(s.name, '-') AS owner_name,
					       sg.grade AS grade,
					       DATE_FORMAT(sg.expired_at, '%Y-%m-%d') AS grade_expired_at,
					       CASE cr.status WHEN 'ACTIVE' THEN '활성화' ELSE '삭제' END AS status,
					       DATE_FORMAT(cr.created_at, '%Y-%m-%d') AS joined_at,
					       cr.created_at
					FROM company_registered cr
					LEFT JOIN seller s ON cr.seller_id = s.seller_id
					LEFT JOIN (
						SELECT sg1.company_id,
						       sg1.grade,
						       sg1.expired_at,
						       sg1.updated_at
						FROM seller_grade sg1
						JOIN (\n\t\t\t\t\tSELECT sg2.company_id, MAX(sg2.updated_at) AS max_updated\n\t\t\t\t\tFROM seller_grade sg2\n\t\t\t\t\tGROUP BY sg2.company_id\n\t\t\t\t) latest ON sg1.company_id = latest.company_id AND sg1.updated_at = latest.max_updated
					) sg ON cr.company_id = sg.company_id
				) AS companies
				""";

		StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
		List<Object> params = new ArrayList<>();
		appendFilters(whereClause, params, keyword, companyName, businessNumber, grade, status, fromDate, toDate);

		String dataSql = """
				SELECT CONCAT('company-', company_id) AS id,
				       company_name,
				       owner_name,
				       business_number,
				       grade,
				       grade_expired_at,
				       status,
				       joined_at
				"""
				+ baseQuery
				+ whereClause
				+ " ORDER BY created_at DESC LIMIT ? OFFSET ?";

		List<AdminCompanyResponse> companies = jdbcTemplate.query(
				dataSql,
				(rs, rowNum) -> new AdminCompanyResponse(
						rs.getString("id"),
						rs.getString("company_name"),
						rs.getString("owner_name"),
						rs.getString("business_number"),
						rs.getString("grade"),
						rs.getString("grade_expired_at"),
						rs.getString("status"),
						rs.getString("joined_at")
				),
				buildPageParams(params, normalizedSize, offset)
		);

		String countSql = "SELECT COUNT(*) " + baseQuery + whereClause;
		Long total = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);
		long totalCount = total == null ? 0L : total;
		int totalPages = (int) Math.ceil(totalCount / (double) normalizedSize);

		return new AdminCompanyPageResponse(companies, normalizedPage, normalizedSize, totalCount, totalPages);
	}

	private void appendFilters(
			StringBuilder whereClause,
			List<Object> params,
			String keyword,
			String companyName,
			String businessNumber,
			String grade,
			String status,
			String fromDate,
			String toDate
	) {
		if (keyword != null && !keyword.trim().isEmpty()) {
			String lowered = "%" + keyword.trim().toLowerCase() + "%";
			whereClause.append(" AND (LOWER(company_name) LIKE ? OR LOWER(owner_name) LIKE ? OR business_number LIKE ?)");
			params.add(lowered);
			params.add(lowered);
			params.add("%" + keyword.trim() + "%");
		}
		if (companyName != null && !companyName.trim().isEmpty()) {
			whereClause.append(" AND LOWER(company_name) LIKE ?");
			params.add("%" + companyName.trim().toLowerCase() + "%");
		}
		if (businessNumber != null && !businessNumber.trim().isEmpty()) {
			whereClause.append(" AND business_number LIKE ?");
			params.add("%" + businessNumber.trim() + "%");
		}
		if (grade != null && !grade.isBlank() && !"전체".equals(grade)) {
			whereClause.append(" AND grade = ?");
			params.add(grade);
		}
		if (status != null && !status.isBlank() && !"전체".equals(status)) {
			whereClause.append(" AND status = ?");
			params.add(status);
		}
		if (fromDate != null && !fromDate.isBlank()) {
			whereClause.append(" AND DATE(created_at) >= ?");
			params.add(fromDate);
		}
		if (toDate != null && !toDate.isBlank()) {
			whereClause.append(" AND DATE(created_at) <= ?");
			params.add(toDate);
		}
	}

	private Object[] buildPageParams(List<Object> params, int size, int offset) {
		List<Object> allParams = new ArrayList<>(params);
		allParams.add(size);
		allParams.add(offset);
		return allParams.toArray();
	}
}

