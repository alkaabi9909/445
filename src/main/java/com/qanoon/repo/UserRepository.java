package com.qanoon.repo;

import com.qanoon.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** مستودع المستخدمين. */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String u);

    List<User> findByActiveTrue();

    List<User> findByRole_CodeAndActiveTrue(String code);

    boolean existsByUsername(String u);
}
