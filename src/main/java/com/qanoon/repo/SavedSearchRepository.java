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
public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    List<SavedSearch> findByUserIdAndSavedTrueOrderByCreatedAtDesc(Long id);
    List<SavedSearch> findByUserIdAndSavedFalseOrderByCreatedAtDesc(Long id, Pageable p);
    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);
}