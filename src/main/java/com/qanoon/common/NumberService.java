package com.qanoon.common;

import com.qanoon.domain.NumberSequence;
import com.qanoon.repo.NumberSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * توليد أرقام المستندات بصيغة "PREFIX-YYYY-0001".
 * العدّاد يبدأ من جديد مع كل سنة ميلادية.
 */
@Service
@RequiredArgsConstructor
public class NumberService {

    private final NumberSequenceRepository repo;

    /** مفاتيح العدّادات: FIN، CASE، EXE، CON، RCP، CERT، ARC. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized String next(String seqKey, String prefix) {
        int year = LocalDate.now().getYear();
        NumberSequence seq = repo.findBySeqKeyAndSeqYear(seqKey, year).orElseGet(() -> {
            NumberSequence s = new NumberSequence();
            s.setSeqKey(seqKey);
            s.setSeqYear(year);
            s.setPrefix(prefix);
            s.setLastValue(0L);
            return s;
        });
        seq.setLastValue(seq.getLastValue() + 1);
        if (seq.getPrefix() == null || seq.getPrefix().isBlank()) {
            seq.setPrefix(prefix);
        }
        repo.save(seq);
        return String.format("%s-%d-%04d", prefix, year, seq.getLastValue());
    }
}
