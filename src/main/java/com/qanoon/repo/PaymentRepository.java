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
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByFinancialFileIdOrderByPaymentDateDesc(Long id);
    List<Payment> findByExecutionFileIdOrderByPaymentDateDesc(Long id);
    List<Payment> findByFinancialFileIdAndStatus(Long id, Enums.PaymentStatus s);
    List<Payment> findByExecutionFileIdAndStatus(Long id, Enums.PaymentStatus s);
    List<Payment> findByInstallmentId(Long id);
    List<Payment> findByStatus(Enums.PaymentStatus s);
    List<Payment> findByPaymentDateBetweenAndStatus(LocalDate a, LocalDate b, Enums.PaymentStatus s);
    Optional<Payment> findByReceiptNumber(String r);
}