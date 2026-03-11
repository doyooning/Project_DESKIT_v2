package com.deskit.deskit.admin.service;

import com.deskit.deskit.admin.dto.AdminUserPageResponse;
import com.deskit.deskit.admin.dto.AdminUserResponse;
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

class AdminUserServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new AdminUserService(jdbcTemplate);
    }

    @Test
    void listUsersBuildsFilteredQueryAndNormalizesPageSize() throws Exception {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<AdminUserResponse> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn("member-1");
                    when(rs.getString("email")).thenReturn("user@test.com");
                    when(rs.getString("name")).thenReturn("name");
                    when(rs.getString("type")).thenReturn("일반회원");
                    when(rs.getString("status")).thenReturn("활성");
                    when(rs.getString("phone")).thenReturn("010");
                    when(rs.getString("joined_at")).thenReturn("2026-01-01");
                    when(rs.getString("provider")).thenReturn("Email");
                    when(rs.getBoolean("marketing_agreed")).thenReturn(true);
                    return List.of(mapper.mapRow(rs, 0));
                });

        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(null);

        AdminUserPageResponse response = service.listUsers(
                -1,
                0,
                " key ",
                "일반회원",
                "활성",
                "2026-01-01",
                "2026-12-31"
        );

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.total()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.items()).hasSize(1);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertThat(sqlCaptor.getValue())
                .contains("LOWER(email) LIKE ?")
                .contains("type = ?")
                .contains("status = ?")
                .contains("DATE(created_at) >= ?")
                .contains("DATE(created_at) <= ?");
        assertThat(args).containsExactly(
                "%key%",
                "%key%",
                "%key%",
                "일반회원",
                "활성",
                "2026-01-01",
                "2026-12-31",
                1,
                0
        );
    }

    @Test
    void listUsersWithoutFiltersUsesPagingAndTotal() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(11L);

        AdminUserPageResponse response = service.listUsers(2, 5, null, "전체", "전체", null, null);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.total()).isEqualTo(11L);
        assertThat(response.totalPages()).isEqualTo(3);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbcTemplate).query(any(String.class), any(RowMapper.class), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).containsExactly(5, 10);
    }

    @Test
    void listUsersWithBlankFiltersDoesNotAppendConditions() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(0L);

        service.listUsers(0, 10, " ", " ", " ", " ", " ");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertThat(sqlCaptor.getValue()).doesNotContain("LOWER(email) LIKE ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("type = ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("status = ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("DATE(created_at) >=");
        assertThat(sqlCaptor.getValue()).doesNotContain("DATE(created_at) <=");
        assertThat(argsCaptor.getValue()).containsExactly(10, 0);
    }

    @Test
    void listUsersWithAllLiteralSkipsTypeAndStatusFilters() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(0L);

        String all = "\uC804\uCCB4";
        service.listUsers(0, 10, null, all, all, null, null);
        org.mockito.Mockito.verify(jdbcTemplate).query(any(String.class), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void listUsersWithNullTypeAndStatusSkipsTypeStatusFilters() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class), eq(Long.class))).thenReturn(0L);

        service.listUsers(0, 10, "keyword", null, null, null, null);

        org.mockito.Mockito.verify(jdbcTemplate).query(any(String.class), any(RowMapper.class), any(Object[].class));
    }
}
