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
public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    Optional<Consultation> findByConsultationNumber(String n);
    List<Consultation> findByConsultant_Id(Long id);
    List<Consultation> findByStatus(Enums.ConsultStatus s);
    List<Consultation> findByConsultant_IdAndStatusNotIn(Long id, Collection<Enums.ConsultStatus> st);
    List<Consultation> findByStatusNotIn(Collection<Enums.ConsultStatus> st);
    List<Consultation> findByArchivedFalse();
    long countByConsultant_IdAndStatusNotIn(Long id, Collection<Enums.ConsultStatus> st);
    long countByStatus(Enums.ConsultStatus s);
}