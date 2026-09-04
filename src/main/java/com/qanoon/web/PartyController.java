package com.qanoon.web;

import com.qanoon.common.*;
import com.qanoon.domain.Enums;
import com.qanoon.domain.Party;
import com.qanoon.domain.Permission;
import com.qanoon.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** الموكلون والخصوم/المدينون. */
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyRepository partyRepository;
    private final FinancialFileRepository financialFileRepository;
    private final LegalCaseRepository legalCaseRepository;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @GetMapping
    public List<Party> list(@RequestParam(required = false) String kind,
                            @RequestParam(required = false) String q) {
        securityUtils.require(Permission.CLIENTS_VIEW);
        List<Party> all = partyRepository.findAll();
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        Party.Kind k = parseKind(kind);
        return all.stream()
                .filter(p -> k == null || p.getKind() == k)
                .filter(p -> needle == null || needle.isEmpty()
                        || (p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(needle))
                        || (p.getIdNumber() != null && p.getIdNumber().toLowerCase(Locale.ROOT).contains(needle))
                        || (p.getPhone() != null && p.getPhone().contains(needle)))
                .sorted(Comparator.comparing(Party::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @PostMapping
    public ApiResponse create(@RequestBody Party in) {
        securityUtils.require(Permission.CLIENTS_MANAGE);
        validate(in);
        Party p = new Party();
        apply(p, in);
        Party saved = partyRepository.save(p);
        auditService.log("CREATE", "Party", saved.getId(), saved.getName(),
                "إضافة " + label(saved.getKind()) + ": " + saved.getName());
        return ApiResponse.ok("تمت الإضافة", saved);
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Party in) {
        securityUtils.require(Permission.CLIENTS_MANAGE);
        Party p = partyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("الطرف غير موجود"));
        validate(in);
        apply(p, in);
        Party saved = partyRepository.save(p);
        auditService.log("UPDATE", "Party", saved.getId(), saved.getName(),
                "تعديل بيانات " + label(saved.getKind()) + ": " + saved.getName());
        return ApiResponse.ok("تم حفظ التعديلات", saved);
    }

    /** لا يُحذف طرف مرتبط بملفات — يُعطَّل بدل الحذف حفاظاً على سلامة السجلات. */
    @DeleteMapping("/{id}")
    public ApiResponse deactivate(@PathVariable Long id) {
        securityUtils.require(Permission.CLIENTS_MANAGE);
        Party p = partyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("الطرف غير موجود"));
        boolean linked = financialFileRepository.findAll().stream()
                .anyMatch(f -> matches(f.getClient(), id) || matches(f.getDebtor(), id))
                || legalCaseRepository.findAll().stream()
                .anyMatch(c -> matches(c.getClient(), id) || matches(c.getOpponent(), id));

        p.setActive(false);
        partyRepository.save(p);
        auditService.log("DEACTIVATE", "Party", id, p.getName(), "تعطيل الطرف: " + p.getName());
        return ApiResponse.ok(linked
                ? "الطرف مرتبط بملفات قائمة فلم يُحذف — تم تعطيله بدلاً من ذلك"
                : "تم تعطيل الطرف");
    }

    private static boolean matches(Party p, Long id) {
        return p != null && id.equals(p.getId());
    }

    private static void validate(Party in) {
        if (in.getName() == null || in.getName().isBlank()) {
            throw new BusinessException("اسم الطرف مطلوب");
        }
        if (in.getKind() == null) {
            throw new BusinessException("نوع الطرف مطلوب (موكل أو مدين)");
        }
    }

    private static void apply(Party p, Party in) {
        p.setKind(in.getKind());
        p.setName(in.getName().trim());
        p.setPartyType(in.getPartyType() == null ? Enums.PartyType.INDIVIDUAL : in.getPartyType());
        p.setIdNumber(in.getIdNumber());
        p.setPhone(in.getPhone());
        p.setEmail(in.getEmail());
        p.setAddress(in.getAddress());
        p.setNotes(in.getNotes());
        p.setActive(in.isActive());
    }

    private static Party.Kind parseKind(String raw) {
        return EnumParser.optional(Party.Kind.class, raw, "نوع الطرف");
    }

    private static String label(Party.Kind k) {
        return k == Party.Kind.CLIENT ? "موكل" : "مدين/خصم";
    }
}
