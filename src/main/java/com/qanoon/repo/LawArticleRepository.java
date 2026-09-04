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
public interface LawArticleRepository extends JpaRepository<LawArticle, Long>, JpaSpecificationExecutor<LawArticle> {
    List<LawArticle> findByChapterIdOrderBySortOrderAsc(Long id);
    List<LawArticle> findByLawCodeIdOrderBySortOrderAsc(Long id);
}