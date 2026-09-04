package com.qanoon.service;

import com.qanoon.common.NotFoundException;
import com.qanoon.common.SecurityUtils;
import com.qanoon.domain.ArchiveItem;
import com.qanoon.domain.SavedSearch;
import com.qanoon.repo.SavedSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** البحث الشامل عبر الأرشيف والمواد القانونية، وحفظ البحوث وآخر خمسة منها. */
@Service
@RequiredArgsConstructor
public class SearchService {

    /** عدد البحوث الأخيرة المحفوظة لكل مستخدم. */
    private static final int RECENT_LIMIT = 5;

    private final ArchiveService archiveService;
    private final LawService lawService;
    private final SavedSearchRepository savedSearchRepository;
    private final SecurityUtils securityUtils;

    public record Hit(String type, String label, Long id, String title, String snippet,
                      LocalDate date, String link) {}

    public record SavedSearches(List<SavedSearch> saved, List<SavedSearch> recent) {}

    /** بحث موحّد يجمع نتائج الأرشيف والمواد القانونية. */
    @Transactional(readOnly = true)
    public List<Hit> searchAll(String q, LocalDate from, LocalDate to, int limit) {
        List<Hit> hits = new ArrayList<>();
        int cap = limit <= 0 ? 50 : Math.min(limit, 200);

        for (ArchiveItem a : archiveService.search(q, null, null, from, to, 0, cap).getContent()) {
            hits.add(new Hit(
                    a.getItemType() == null ? "ARCHIVE" : a.getItemType().name(),
                    a.getItemType() == null ? "أرشيف" : a.getItemType().label(),
                    a.getId(),
                    a.getTitle(),
                    snippet(a.getSummary() != null ? a.getSummary() : a.getContent()),
                    a.getItemDate(),
                    linkOf(a)));
        }
        for (LawService.SearchHit s : lawService.search(q)) {
            hits.add(new Hit("LAW_ARTICLE", "مادة قانونية", s.articleId(),
                    "المادة " + s.articleNumber() + (s.title() == null ? "" : " — " + s.title()),
                    snippet(s.articleText()), null,
                    "#/laws/" + s.lawCodeId()));
        }
        return hits.size() > cap ? hits.subList(0, cap) : hits;
    }

    // ---------- البحوث المحفوظة وآخر خمسة ----------

    @Transactional(readOnly = true)
    public SavedSearches mine() {
        Long uid = securityUtils.currentUserId();
        return new SavedSearches(
                savedSearchRepository.findByUserIdAndSavedTrueOrderByCreatedAtDesc(uid),
                savedSearchRepository.findByUserIdAndSavedFalseOrderByCreatedAtDesc(
                        uid, PageRequest.of(0, RECENT_LIMIT)));
    }

    @Transactional
    public SavedSearch save(String name, String queryText, String filtersJson) {
        Long uid = securityUtils.currentUserId();
        SavedSearch s = new SavedSearch();
        s.setUserId(uid);
        s.setName(name == null || name.isBlank() ? null : name.trim());
        s.setQueryText(queryText);
        s.setFiltersJson(filtersJson);
        s.setSaved(s.getName() != null);
        SavedSearch out = savedSearchRepository.save(s);
        if (!out.isSaved()) {
            trimRecent(uid);
        }
        return out;
    }

    /** يبقي آخر خمسة بحوث فقط لكل مستخدم. */
    private void trimRecent(Long userId) {
        List<SavedSearch> recent = savedSearchRepository
                .findByUserIdAndSavedFalseOrderByCreatedAtDesc(userId, PageRequest.of(0, 100));
        if (recent.size() > RECENT_LIMIT) {
            savedSearchRepository.deleteAll(recent.subList(RECENT_LIMIT, recent.size()));
        }
    }

    @Transactional
    public void delete(Long id) {
        Long uid = securityUtils.currentUserId();
        SavedSearch s = savedSearchRepository.findByIdAndUserId(id, uid)
                .orElseThrow(() -> new NotFoundException("البحث المحفوظ غير موجود"));
        savedSearchRepository.delete(s);
    }

    private static String linkOf(ArchiveItem a) {
        if (a.getSourceType() == null || a.getSourceId() == null) {
            return "#/archive";
        }
        return switch (a.getSourceType()) {
            case "FINANCIAL_FILE" -> "#/financial/" + a.getSourceId();
            case "CASE" -> "#/cases/" + a.getSourceId();
            case "EXECUTION" -> "#/execution/" + a.getSourceId();
            case "CONSULTATION" -> "#/consultations/" + a.getSourceId();
            default -> "#/archive";
        };
    }

    private static String snippet(String text) {
        if (text == null) return null;
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 220 ? t : t.substring(0, 220) + "…";
    }
}
