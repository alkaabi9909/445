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
public interface InstallmentRepository extends JpaRepository<Installment, Long> {
    List<Installment> findByFinancialFileIdOrderBySeqAsc(Long id);
    List<Installment> findByPlan_IdOrderBySeqAsc(Long planId);
    List<Installment> findByDueDateBeforeAndStatusIn(LocalDate d, Collection<Enums.InstallmentStatus> st);
    List<Installment> findByDueDateBetweenAndStatusIn(LocalDate a, LocalDate b, Collection<Enums.InstallmentStatus> st);
}