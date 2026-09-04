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
public interface ExecutionFileRepository extends JpaRepository<ExecutionFile, Long> {
    Optional<ExecutionFile> findByExecutionNumber(String n);
    Optional<ExecutionFile> findByCaseId(Long id);
    List<ExecutionFile> findByStatus(Enums.ExecStatus s);
    List<ExecutionFile> findByAssignedLawyer_Id(Long id);
    List<ExecutionFile> findByArchivedFalse();
    long countByStatus(Enums.ExecStatus s);
}