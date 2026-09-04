package com.qanoon.repo;

import com.qanoon.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** مستودع الأدوار الوظيفية وصلاحياتها. */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String c);
}
