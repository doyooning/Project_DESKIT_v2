package com.deskit.deskit.admin.repository;

import com.deskit.deskit.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Admin findByLoginId(String loginId);
}
