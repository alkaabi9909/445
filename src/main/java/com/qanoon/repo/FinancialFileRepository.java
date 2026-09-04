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
public interface FinancialFileRepository extends JpaRepository<FinancialFile, Long> {
    Optional<FinancialFile> findByFileNumber(String n);
    List<FinancialFile> findByStatus(Enums.FileStatus s);
    List<FinancialFile> findByAssignedLawyer_Id(Long id);
    List<FinancialFile> findByArchivedFalse();
    long countByStatus(Enums.FileStatus s);
    long countByArchivedFalse();
}