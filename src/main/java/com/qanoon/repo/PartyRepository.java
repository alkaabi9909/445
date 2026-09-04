package com.qanoon.repo;

import com.qanoon.domain.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** مستودع الأطراف: الموكلون والمدينون/الخصوم. */
@Repository
public interface PartyRepository extends JpaRepository<Party, Long>, JpaSpecificationExecutor<Party> {

    List<Party> findByKindAndActiveTrue(Party.Kind k);

    List<Party> findByKindAndNameContainingIgnoreCase(Party.Kind k, String n);
}
