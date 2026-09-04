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
public interface ExecutionOrderRepository extends JpaRepository<ExecutionOrder, Long> {
    List<ExecutionOrder> findByExecutionFileId(Long id);
    List<ExecutionOrder> findByExecutionFileIdAndStatus(Long id, Enums.OrderStatus s);
}