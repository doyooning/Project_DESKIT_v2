package com.deskit.deskit.admin.service;

import com.deskit.deskit.admin.dto.AdminUserPageResponse;
import com.deskit.deskit.admin.dto.AdminUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

	private final JdbcTemplate jdbcTemplate;

	public AdminUserPageResponse listUsers(
			int page,
			int size,
			String keyword,
			String type,
			String status,
			String fromDate,
			String toDate
	) {
		int normalizedPage = Math.max(page, 0);
		int normalizedSize = Math.max(size, 1);
		int offset = normalizedPage * normalizedSize;

		String baseQuery = """
				FROM (
					SELECT CONCAT('member-', member_id) AS id,
					       login_id AS email,
					       name,
					       '일반회원' AS type,
					       CASE status WHEN 'ACTIVE' THEN '활성화' ELSE '비활성화' END AS status,
					       phone,
					       DATE_FORMAT(created_at, '%Y-%m-%d') AS joined_at,
					       'Email' AS provider,
					       is_agreed AS marketing_agreed,
					       created_at
					FROM member
					UNION ALL
					SELECT CONCAT('seller-', seller_id) AS id,
					       login_id AS email,
					       name,
					       '판매자' AS type,
					       CASE status WHEN 'ACTIVE' THEN '활성화' ELSE '비활성화' END AS status,
					       phone,
					       DATE_FORMAT(created_at, '%Y-%m-%d') AS joined_at,
					       'Email' AS provider,
					       is_agreed AS marketing_agreed,
					       created_at
					FROM seller
				) AS users
				""";

		StringBuilder whereClause = new StringBuilder(" WHERE 1=1");
		List<Object> params = new ArrayList<>();
		appendFilters(whereClause, params, keyword, type, status, fromDate, toDate);

		String dataSql = """
				SELECT id, email, name, type, status, phone, joined_at, provider, marketing_agreed
				"""
				+ baseQuery
				+ whereClause
				+ " ORDER BY created_at DESC LIMIT ? OFFSET ?";

		List<AdminUserResponse> users = jdbcTemplate.query(
				dataSql,
				(rs, rowNum) -> new AdminUserResponse(
						rs.getString("id"),
						rs.getString("email"),
						rs.getString("name"),
						rs.getString("type"),
						rs.getString("status"),
						rs.getString("phone"),
						rs.getString("joined_at"),
						rs.getString("provider"),
						rs.getBoolean("marketing_agreed")
				),
				buildPageParams(params, normalizedSize, offset)
		);

		String countSql = "SELECT COUNT(*) " + baseQuery + whereClause;
		Long total = jdbcTemplate.queryForObject(countSql, params.toArray(), Long.class);
		long totalCount = total == null ? 0L : total;
		int totalPages = (int) Math.ceil(totalCount / (double) normalizedSize);

		return new AdminUserPageResponse(users, normalizedPage, normalizedSize, totalCount, totalPages);
	}

	private void appendFilters(
			StringBuilder whereClause,
			List<Object> params,
			String keyword,
			String type,
			String status,
			String fromDate,
			String toDate
	) {
		if (keyword != null && !keyword.trim().isEmpty()) {
			String lowered = "%" + keyword.trim().toLowerCase() + "%";
			whereClause.append(" AND (LOWER(email) LIKE ? OR LOWER(name) LIKE ? OR phone LIKE ?)");
			params.add(lowered);
			params.add(lowered);
			params.add("%" + keyword.trim() + "%");
		}
		if (type != null && !type.isBlank() && !"전체".equals(type)) {
			whereClause.append(" AND type = ?");
			params.add(type);
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
