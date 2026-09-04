package com.qanoon.service;

import com.qanoon.common.*;
import com.qanoon.domain.ArchiveItem;
import com.qanoon.domain.Enums;
import com.qanoon.domain.Permission;
import com.qanoon.repo.ArchiveItemRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * الأرشيف الشامل: كل ملف مغلق يُحفظ هنا آلياً بمستنداته،
 * إلى جانب الأحكام والآراء والنماذج والمراجع المضافة يدوياً.
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final ArchiveItemRepository repo;
    private final NumberService numberService;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    // ==================== الأرشفة التلقائية من المسارات ====================

    /**
     * الأرشفة الأساسية. إن وُجد عنصر بنفس المصدر يُحدَّث بدل إنشاء تكرار،
     * حتى لا يتضاعف الأرشيف عند إعادة إغلاق ملف.
     */
    @Transactional
    public ArchiveItem archiveSource(String sourceType, Long sourceId, String sourceNumber,
                                     Enums.ArchiveType itemType, String title, String summary,
                                     String content, String clientName, String courtName,
                                     LocalDate itemDate) {
        if (sourceType == null || sourceId == null) {
            throw new BusinessException("تعذّرت الأرشفة: مصدر الملف غير محدد");
        }
        ArchiveItem item = repo.findBySourceTypeAndSourceId(sourceType, sourceId).orElseGet(ArchiveItem::new);
        if (item.getReferenceNo() == null) {
            item.setReferenceNo(numberService.next("ARC", "ARC"));
        }
        item.setItemType(itemType == null ? typeOf(sourceType) : itemType);
        item.setTitle(cut(title, 400));
        item.setSummary(cut(summary, 500));
        item.setContent(cut(content, 4000));
        item.setClientName(cut(clientName, 200));
        item.setCourtName(cut(courtName, 150));
        item.setItemDate(itemDate == null ? LocalDate.now() : itemDate);
        item.setSourceType(sourceType);
        item.setSourceId(sourceId);
        item.setSourceNumber(cut(sourceNumber, 40));
        item.setCategory(item.getItemType().label());
        item.setKeywords(cut(buildKeywords(sourceNumber, clientName, title), 500));
        ArchiveItem saved = repo.save(item);
        auditService.log("ARCHIVE", "ArchiveItem", saved.getId(), saved.getReferenceNo(),
                "أُرشف " + saved.getItemType().label() + " — " + saved.getTitle());
        return saved;
    }

    /** صيغة العقد: (النوع، المعرّف، العنوان، الملخص، المحتوى، التاريخ، اسم الموكل). */
    @Transactional
    public ArchiveItem archiveSource(String sourceType, Long sourceId, String title, String summary,
                                     String content, LocalDate itemDate, String clientName) {
        return archiveSource(sourceType, sourceId, null, typeOf(sourceType), title, summary,
                content, clientName, null, itemDate);
    }

    /** صيغة مسار الاستشارات. */
    @Transactional
    public ArchiveItem archiveSource(String sourceType, Long sourceId, String sourceNumber,
                                     String title, String content, String clientName) {
        return archiveSource(sourceType, sourceId, sourceNumber, typeOf(sourceType), title, title,
                content, clientName, null, LocalDate.now());
    }

    /** صيغة مسار القضايا. */
    @Transactional
    public ArchiveItem archiveSource(Enums.ArchiveType itemType, String sourceType, Long sourceId,
                                     String sourceNumber, String title, String content,
                                     String clientName, String courtName) {
        return archiveSource(sourceType, sourceId, sourceNumber, itemType, title, title,
                content, clientName, courtName, LocalDate.now());
    }

    /** صيغة مسار التنفيذ. */
    @Transactional
    public ArchiveItem archiveSource(String sourceType, Long sourceId, String sourceNumber,
                                     Enums.ArchiveType itemType, String title, String content,
                                     String clientName, String courtName, LocalDate itemDate) {
        return archiveSource(sourceType, sourceId, sourceNumber, itemType, title, title,
                content, clientName, courtName, itemDate);
    }

    // ==================== البحث المتقدم ====================

    /** بحث شامل بالعربية عبر العنوان والمحتوى والملخص والكلمات المفتاحية واسم الموكل ورقم الملف. */
    @Transactional(readOnly = true)
    public Page<ArchiveItem> search(String q, String type, String category,
                                    LocalDate from, LocalDate to, int page, int size) {
        securityUtils.require(Permission.ARCHIVE_VIEW);
        // يُحلَّل النوع قبل بناء الاستعلام لا داخله: القيمة المجهولة يجب أن
        // تُرفض بـ 400 فوراً بدل أن تنفجر داخل بناء المواصفة كخطأ خادم.
        Enums.ArchiveType itemType = EnumParser.optional(Enums.ArchiveType.class, type, "نوع عنصر الأرشيف");
        Specification<ArchiveItem> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (hasText(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("content"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("keywords"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("clientName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("sourceNumber"), "")), like)));
            }
            if (itemType != null) {
                ps.add(cb.equal(root.get("itemType"), itemType));
            }
            if (hasText(category)) {
                ps.add(cb.equal(root.get("category"), category.trim()));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("itemDate"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("itemDate"), to));
            }
            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
        int p = Math.max(page, 0);
        int s = size <= 0 ? 20 : Math.min(size, 200);
        return repo.findAll(spec, PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "itemDate", "id")));
    }

    @Transactional(readOnly = true)
    public ArchiveItem get(Long id) {
        securityUtils.require(Permission.ARCHIVE_VIEW);
        return repo.findById(id).orElseThrow(() -> new NotFoundException("عنصر الأرشيف غير موجود"));
    }

    // ==================== الإدارة اليدوية ====================

    @Transactional
    public ArchiveItem create(ArchiveItem in) {
        securityUtils.require(Permission.ARCHIVE_MANAGE);
        if (in.getTitle() == null || in.getTitle().isBlank()) {
            throw new BusinessException("عنوان العنصر مطلوب");
        }
        if (in.getItemType() == null) {
            throw new BusinessException("نوع العنصر مطلوب");
        }
        ArchiveItem item = new ArchiveItem();
        item.setReferenceNo(numberService.next("ARC", "ARC"));
        applyEditable(item, in);
        ArchiveItem saved = repo.save(item);
        auditService.log("CREATE", "ArchiveItem", saved.getId(), saved.getReferenceNo(),
                "إضافة عنصر أرشيف: " + saved.getTitle());
        return saved;
    }

    @Transactional
    public ArchiveItem update(Long id, ArchiveItem in) {
        securityUtils.require(Permission.ARCHIVE_MANAGE);
        ArchiveItem item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("عنصر الأرشيف غير موجود"));
        applyEditable(item, in);
        ArchiveItem saved = repo.save(item);
        auditService.log("UPDATE", "ArchiveItem", saved.getId(), saved.getReferenceNo(),
                "تعديل عنصر أرشيف: " + saved.getTitle());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        securityUtils.require(Permission.ARCHIVE_MANAGE);
        ArchiveItem item = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("عنصر الأرشيف غير موجود"));
        if (item.getSourceType() != null) {
            throw new BusinessException(
                    "لا يجوز حذف عنصر أُرشف تلقائياً من ملف مغلق — الأرشيف سجل دائم للملفات المنتهية");
        }
        repo.delete(item);
        auditService.log("DELETE", "ArchiveItem", id, item.getReferenceNo(),
                "حذف عنصر أرشيف: " + item.getTitle());
    }

    /** أعمدة التصدير إلى Excel. */
    public List<String> exportHeaders() {
        return List.of("المرجع", "النوع", "العنوان", "التصنيف", "التاريخ", "الموكل", "رقم الملف");
    }

    public List<Object> exportRow(ArchiveItem a) {
        return List.of(
                nz(a.getReferenceNo()),
                a.getItemType() == null ? "" : a.getItemType().label(),
                nz(a.getTitle()),
                nz(a.getCategory()),
                a.getItemDate() == null ? "" : a.getItemDate(),
                nz(a.getClientName()),
                nz(a.getSourceNumber()));
    }

    // ==================== أدوات داخلية ====================

    private void applyEditable(ArchiveItem item, ArchiveItem in) {
        item.setItemType(in.getItemType());
        item.setTitle(cut(in.getTitle(), 400));
        item.setSummary(cut(in.getSummary(), 500));
        item.setContent(cut(in.getContent(), 4000));
        item.setKeywords(cut(in.getKeywords(), 500));
        item.setCategory(in.getCategory() == null || in.getCategory().isBlank()
                ? (in.getItemType() == null ? null : in.getItemType().label())
                : in.getCategory());
        item.setItemDate(in.getItemDate() == null ? LocalDate.now() : in.getItemDate());
        item.setClientName(cut(in.getClientName(), 200));
        item.setCourtName(cut(in.getCourtName(), 150));
        item.setLawCodeId(in.getLawCodeId());
        item.setConfidential(in.isConfidential());
    }

    private static Enums.ArchiveType typeOf(String sourceType) {
        if (sourceType == null) return Enums.ArchiveType.REFERENCE;
        return switch (sourceType) {
            case "FINANCIAL_FILE" -> Enums.ArchiveType.FINANCIAL_FILE;
            case "CASE" -> Enums.ArchiveType.CASE;
            case "EXECUTION" -> Enums.ArchiveType.EXECUTION;
            case "CONSULTATION" -> Enums.ArchiveType.CONSULTATION;
            default -> Enums.ArchiveType.REFERENCE;
        };
    }

    private static String buildKeywords(String number, String client, String title) {
        StringBuilder sb = new StringBuilder();
        if (hasText(number)) sb.append(number).append(' ');
        if (hasText(client)) sb.append(client).append(' ');
        if (hasText(title)) sb.append(title);
        return sb.toString().trim();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
