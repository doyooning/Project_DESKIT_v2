package com.deskit.deskit.admin.service;

import com.deskit.deskit.admin.dto.AdminCompanyPageResponse;
import com.deskit.deskit.admin.dto.AdminCompanyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminCompanyServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AdminCompanyService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new AdminCompanyService(jdbcTemplate);
    }

    @Test
    void listCompaniesBuildsFilteredQueryAndNormalizesPageSize() throws Exception {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<AdminCompanyResponse> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn("company-1");
                    when(rs.getString("company_name")).thenReturn("회사");
                    when(rs.getString("owner_name")).thenReturn("대표");
                    when(rs.getString("business_number")).thenReturn("123-45");
                    when(rs.getString("grade")).thenReturn("A");
                    when(rs.getString("grade_expired_at")).thenReturn("2026-12-31");
                    when(rs.getString("status")).thenReturn("활성");
                    when(rs.getString("joined_at")).thenReturn("2026-01-01");
                    return List.of(mapper.mapRow(rs, 0));
                });

        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(null);

        AdminCompanyPageResponse response = service.listCompanies(
                -1,
                0,
                " key ",
                " 회사 ",
                " 123 ",
                "A",
                "활성",
                "2026-01-01",
                "2026-12-31"
        );

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.total()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.items()).hasSize(1);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("LOWER(company_name) LIKE ?")
                .contains("LOWER(owner_name) LIKE ?")
                .contains("business_number LIKE ?")
                .contains("grade = ?")
                .contains("status = ?")
                .contains("DATE(created_at) >= ?")
                .contains("DATE(created_at) <= ?");
        assertThat(argsCaptor.getValue()).containsExactly(
                "%key%",
                "%key%",
                "%key%",
                "%회사%",
                "%123%",
                "A",
                "활성",
                "2026-01-01",
                "2026-12-31",
                1,
                0
        );
    }

    @Test
    void listCompaniesWithoutFiltersUsesPagingAndTotal() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(21L);

        AdminCompanyPageResponse response = service.listCompanies(1, 10, null, null, null, "전체", "전체", null, null);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.total()).isEqualTo(21L);
        assertThat(response.totalPages()).isEqualTo(3);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbcTemplate).query(any(String.class), any(RowMapper.class), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).containsExactly(10, 10);
    }

    @Test
    void listCompaniesWithBlankFiltersDoesNotAppendConditions() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(0L);

        service.listCompanies(0, 10, " ", " ", " ", " ", " ", " ", " ");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue()).doesNotContain("LOWER(company_name) LIKE ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("LOWER(owner_name) LIKE ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("business_number LIKE ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("grade = ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("status = ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("DATE(created_at) >=");
        assertThat(sqlCaptor.getValue()).doesNotContain("DATE(created_at) <=");
        assertThat(argsCaptor.getValue()).containsExactly(10, 0);
    }

    @Test
    void listCompaniesWithAllLiteralSkipsGradeAndStatusFilters() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(0L);

        String all = "\uC804\uCCB4";
        service.listCompanies(0, 10, null, null, null, all, all, null, null);
        org.mockito.Mockito.verify(jdbcTemplate).query(any(String.class), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void listCompaniesWithNullGradeAndStatusSkipsGradeStatusFilters() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(0L);

        service.listCompanies(0, 10, "keyword", null, null, null, null, null, null);

        org.mockito.Mockito.verify(jdbcTemplate).query(any(String.class), any(RowMapper.class), any(Object[].class));
    }
}
