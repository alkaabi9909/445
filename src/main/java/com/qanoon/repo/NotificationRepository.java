package com.qanoon.repo;

import com.qanoon.domain.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long id, Pageable p);
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long id, Pageable p);
    long countByUserIdAndReadFalse(Long id);
    boolean existsByDedupeKey(String k);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}