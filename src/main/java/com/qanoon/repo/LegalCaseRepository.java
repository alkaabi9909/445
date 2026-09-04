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
public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {
    Optional<LegalCase> findByCaseNumber(String n);
    List<LegalCase> findByStatus(Enums.CaseStatus s);
    List<LegalCase> findByAssignedLawyer_Id(Long id);
    List<LegalCase> findByJudgmentFinalTrueAndExecutionFileIdIsNull();
    List<LegalCase> findByAppealDeadlineBetween(LocalDate a, LocalDate b);
    List<LegalCase> findByArchivedFalse();
    Optional<LegalCase> findBySourceFinancialFileId(Long id);
    long countByStatus(Enums.CaseStatus s);
}