package com.qanoon.service;

import com.qanoon.common.*;
import com.qanoon.domain.LawArticle;
import com.qanoon.domain.LawChapter;
import com.qanoon.domain.LawCode;
import com.qanoon.domain.Permission;
import com.qanoon.repo.LawArticleRepository;
import com.qanoon.repo.LawChapterRepository;
import com.qanoon.repo.LawCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** المكتبة القانونية: تشريع ← باب ← فصل ← مادة. */
@Service
@RequiredArgsConstructor
public class LawService {

    private final LawCodeRepository codeRepo;
    private final LawChapterRepository chapterRepo;
    private final LawArticleRepository articleRepo;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    // ---------- عرض ----------

    public record ArticleView(Long id, String articleNumber, String title, String articleText,
                              String keywords, Long chapterId, int sortOrder) {}

    public record ChapterNode(Long id, String level, String chapterNumber, String title, int sortOrder,
                              List<ChapterNode> children, List<ArticleView> articles) {}

    public record LawTree(LawCode code, List<ChapterNode> chapters, List<ArticleView> looseArticles) {}

    public record SearchHit(Long articleId, String articleNumber, String title, String articleText,
                            Long lawCodeId, String lawTitle, String chapterTitle) {}

    @Transactional(readOnly = true)
    public List<LawCode> codes() {
        securityUtils.require(Permission.ARCHIVE_VIEW);
        return codeRepo.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public LawTree tree(Long lawCodeId) {
        securityUtils.require(Permission.ARCHIVE_VIEW);
        LawCode code = codeRepo.findById(lawCodeId)
                .orElseThrow(() -> new NotFoundException("التشريع غير موجود"));
        List<ChapterNode> roots = chapterRepo
                .findByLawCodeIdAndParentIdIsNullOrderBySortOrderAsc(lawCodeId).stream()
                .map(this::node)
                .toList();
        List<ArticleView> loose = articleRepo.findByLawCodeIdOrderBySortOrderAsc(lawCodeId).stream()
                .filter(a -> a.getChapterId() == null)
                .map(LawService::view)
                .toList();
        return new LawTree(code, roots, loose);
    }

    private ChapterNode node(LawChapter c) {
        List<ChapterNode> children = chapterRepo.findByParentIdOrderBySortOrderAsc(c.getId()).stream()
                .map(this::node)
                .toList();
        List<ArticleView> articles = articleRepo.findByChapterIdOrderBySortOrderAsc(c.getId()).stream()
                .map(LawService::view)
                .toList();
        return new ChapterNode(c.getId(), c.getLevel().name(), c.getChapterNumber(), c.getTitle(),
                c.getSortOrder(), children, articles);
    }

    private static ArticleView view(LawArticle a) {
        return new ArticleView(a.getId(), a.getArticleNumber(), a.getTitle(), a.getArticleText(),
                a.getKeywords(), a.getChapterId(), a.getSortOrder());
    }

    /** بحث في نصوص المواد وأرقامها وعناوينها وكلماتها المفتاحية — يعمل بالعربية. */
    @Transactional(readOnly = true)
    public List<SearchHit> search(String q) {
        securityUtils.require(Permission.ARCHIVE_VIEW);
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        return articleRepo.findAll().stream()
                .filter(a -> contains(a.getArticleText(), needle)
                        || contains(a.getTitle(), needle)
                        || contains(a.getArticleNumber(), needle)
                        || contains(a.getKeywords(), needle))
                .limit(200)
                .map(a -> new SearchHit(
                        a.getId(), a.getArticleNumber(), a.getTitle(), a.getArticleText(),
                        a.getLawCodeId(),
                        codeRepo.findById(a.getLawCodeId()).map(LawCode::getTitle).orElse(null),
                        a.getChapterId() == null ? null
                                : chapterRepo.findById(a.getChapterId()).map(LawChapter::getTitle).orElse(null)))
                .toList();
    }

    // ---------- إدارة ----------

    @Transactional
    public LawCode createCode(LawCode in) {
        securityUtils.require(Permission.ARCHIVE_MANAGE);
        if (in.getTitle() == null || in.getTitle().isBlank()) {
            throw new BusinessException("عنوان التشريع مطلوب");
        }
        LawCode c = new LawCode();
        c.setTitle(in.getTitle().trim());
        c.setLawNumber(in.getLawNumber());
        c.setIssueYear(in.getIssueYear());
        c.setJurisdiction(in.getJurisdiction());
        c.setDescription(in.getDescription());
        c.setActive(true);
        LawCode saved = codeRepo.save(c);
        auditService.log("CREATE", "LawCode", saved.getId(), saved.getTitle(),
                "إضافة تشريع: " + saved.getTitle());
        return saved;
    }

    @Transactional
    public LawChapter createChapter(Long lawCodeId, LawChapter in) {
        securityUtils.require(Permission.ARCHIVE_MANAGE);
        if (in.getTitle() == null || in.getTitle().isBlank()) {
            throw new BusinessException("عنوان الباب/الفصل مطلوب");
        }
        codeRepo.findById(lawCodeId).orElseThrow(() -> new NotFoundException("التشريع غير موجود"));
        LawChapter c = new LawChapter();
        c.setLawCodeId(lawCodeId);
        c.setParentId(in.getParentId());
        c.setLevel(in.getParentId() == null ? LawChapter.Level.BAB : LawChapter.Level.FASL);
        c.setChapterNumber(in.getChapterNumber());
        c.setTitle(in.getTitle().trim());
        c.setSortOrder(in.getSortOrder());
        LawChapter saved = chapterRepo.save(c);
        auditService.log("CREATE", "LawChapter", saved.getId(), saved.getTitle(),
                "إضافة " + (saved.getLevel() == LawChapter.Level.BAB ? "باب" : "فصل") + ": " + saved.getTitle());
        return saved;
    }

    @Transactional
    public LawArticle createArticle(Long lawCodeId, LawArticle in) {
        securityUtils.require(Permission.ARCHIVE_MANAGE);
        if (in.getArticleNumber() == null || in.getArticleNumber().isBlank()) {
            throw new BusinessException("رقم المادة مطلوب");
        }
        if (in.getArticleText() == null || in.getArticleText().isBlank()) {
            throw new BusinessException("نص المادة مطلوب");
        }
        codeRepo.findById(lawCodeId).orElseThrow(() -> new NotFoundException("التشريع غير موجود"));
        LawArticle a = new LawArticle();
        a.setLawCodeId(lawCodeId);
        a.setChapterId(in.getChapterId());
        a.setArticleNumber(in.getArticleNumber().trim());
        a.setTitle(in.getTitle());
        a.setArticleText(in.getArticleText());
        a.setKeywords(in.getKeywords());
        a.setSortOrder(in.getSortOrder());
        LawArticle saved = articleRepo.save(a);
        auditService.log("CREATE", "LawArticle", saved.getId(), saved.getArticleNumber(),
                "إضافة المادة " + saved.getArticleNumber());
        return saved;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
