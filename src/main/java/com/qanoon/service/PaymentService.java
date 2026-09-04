package com.qanoon.service;

import com.qanoon.common.*;
import com.qanoon.domain.*;
import com.qanoon.dto.FinancialDtos;
import com.qanoon.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * الدفعات على الملفات المالية.
 * القاعدة الذهبية: المالي يقود الحالة — لا يتغيّر شيء إلا بالدفعات المؤكدة.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final InstallmentRepository installmentRepo;
    private final FinancialFileRepository fileRepo;
    private final FinancialService financialService;
    private final NumberService numberService;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    // ==================== تسجيل دفعة ====================

    /** كل دفعة تُنشأ "بانتظار التأكيد" — ولا تؤثر على حالة الملف قبل تأكيدها. */
    @Transactional
    public Payment addPayment(Long fileId, FinancialDtos.PaymentRequest req) {
        securityUtils.require(Permission.FINANCIAL_MANAGE);
        FinancialFile file = financialService.getOrThrow(fileId);
        financialService.checkView(file);
        financialService.ensurePaymentsAllowed(file);

        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("مبلغ الدفعة يجب أن يكون أكبر من صفر");
        }
        Enums.PaymentMethod method = parseMethod(req.method());

        Payment p = new Payment();
        p.setFinancialFileId(file.getId());
        p.setReceiptNumber(numberService.next("RCP", "RCP"));
        p.setAmount(req.amount());
        p.setPaymentDate(req.paymentDate() == null ? LocalDate.now() : req.paymentDate());
        p.setMethod(method);
        p.setStatus(Enums.PaymentStatus.PENDING);
        p.setReferenceNo(req.referenceNo());
        p.setBankName(req.bankName());
        p.setPayerName(req.payerName());
        p.setNotes(req.notes());
        p.setReceivedBy(securityUtils.currentUserOrNull());

        if (req.installmentId() != null) {
            Installment inst = requireInstallment(req.installmentId());
            if (!inst.getFinancialFileId().equals(file.getId())) {
                throw new BusinessException("القسط المحدد لا يخص هذا الملف");
            }
            if (inst.getStatus() == Enums.InstallmentStatus.CANCELLED) {
                throw new BusinessException("لا يمكن ربط دفعة بقسط ملغى");
            }
            p.setInstallmentId(inst.getId());
        }

        Payment saved = paymentRepo.save(p);
        auditService.log("CREATE", "Payment", saved.getId(), saved.getReceiptNumber(),
                "تسجيل دفعة بمبلغ " + saved.getAmount() + " على الملف " + file.getFileNumber()
                        + " — بانتظار التأكيد");
        return saved;
    }

    // ==================== تأكيد الدفعة ====================

    @Transactional
    public Payment confirm(Long paymentId) {
        securityUtils.require(Permission.PAYMENT_CONFIRM);
        Payment p = requirePayment(paymentId);
        if (p.getStatus() == Enums.PaymentStatus.CONFIRMED) {
            throw new BusinessException("الدفعة مؤكدة مسبقاً");
        }
        if (p.getStatus() == Enums.PaymentStatus.CANCELLED) {
            throw new BusinessException("لا يمكن تأكيد دفعة ملغاة");
        }
        p.setStatus(Enums.PaymentStatus.CONFIRMED);
        p.setConfirmedBy(securityUtils.currentUserOrNull());
        p.setConfirmedAt(LocalDateTime.now());
        p.setBouncedAt(null);
        p.setBounceReason(null);
        paymentRepo.save(p);

        applyToInstallment(p.getInstallmentId());
        recompute(p);

        auditService.log("CONFIRM", "Payment", p.getId(), p.getReceiptNumber(),
                "تأكيد دفعة بمبلغ " + p.getAmount() + " — أُعيد احتساب حالة الملف تلقائياً");
        return p;
    }

    // ==================== ارتجاع الشيك ====================

    /**
     * ارتجاع شيك: يُخصم المبلغ من المحصّل، وتعود الأقساط التي سدّدها
     * إلى الاستحقاق أو التأخر بحسب تاريخ استحقاقها.
     */
    @Transactional
    public Payment markBounced(Long paymentId, String reason) {
        securityUtils.require(Permission.PAYMENT_CONFIRM);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("سبب الارتجاع إلزامي");
        }
        Payment p = requirePayment(paymentId);
        if (p.getStatus() == Enums.PaymentStatus.BOUNCED) {
            throw new BusinessException("الدفعة مسجّلة كمرتجعة مسبقاً");
        }
        p.setStatus(Enums.PaymentStatus.BOUNCED);
        p.setBouncedAt(LocalDateTime.now());
        p.setBounceReason(reason.trim());
        paymentRepo.save(p);

        applyToInstallment(p.getInstallmentId());
        recompute(p);

        auditService.log("BOUNCE", "Payment", p.getId(), p.getReceiptNumber(),
                "ارتجاع دفعة بمبلغ " + p.getAmount() + " — السبب: " + reason.trim()
                        + " — أُعيدت الأقساط المرتبطة إلى الاستحقاق");
        return p;
    }

    @Transactional
    public Payment cancel(Long paymentId, String reason) {
        securityUtils.require(Permission.PAYMENT_CONFIRM);
        Payment p = requirePayment(paymentId);
        p.setStatus(Enums.PaymentStatus.CANCELLED);
        p.setNotes(appendNote(p.getNotes(), "إلغاء: " + (reason == null ? "" : reason.trim())));
        paymentRepo.save(p);
        applyToInstallment(p.getInstallmentId());
        recompute(p);
        auditService.log("CANCEL", "Payment", p.getId(), p.getReceiptNumber(),
                "إلغاء دفعة بمبلغ " + p.getAmount());
        return p;
    }

    // ==================== إلغاء قسط (بيد الإنسان فقط) ====================

    @Transactional
    public Installment cancelInstallment(Long installmentId, String reason) {
        securityUtils.require(Permission.FINANCIAL_MANAGE);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("سبب إلغاء القسط إلزامي");
        }
        Installment inst = requireInstallment(installmentId);
        if (inst.getStatus() == Enums.InstallmentStatus.PAID) {
            throw new BusinessException("لا يجوز إلغاء قسط مسدد بالكامل");
        }
        if (inst.getStatus() == Enums.InstallmentStatus.CANCELLED) {
            throw new BusinessException("القسط ملغى مسبقاً");
        }
        FinancialFile file = financialService.getOrThrow(inst.getFinancialFileId());
        financialService.checkView(file);
        financialService.ensureNotClosed(file);

        inst.setStatus(Enums.InstallmentStatus.CANCELLED);
        inst.setCancelReason(reason.trim());
        installmentRepo.save(inst);

        financialService.recomputeStatus(file);
        auditService.log("CANCEL", "Installment", inst.getId(), String.valueOf(inst.getSeq()),
                "إلغاء القسط رقم " + inst.getSeq() + " على الملف " + file.getFileNumber()
                        + " — السبب: " + reason.trim());
        return inst;
    }

    // ==================== قراءة ====================

    @Transactional(readOnly = true)
    public List<Payment> forFile(Long fileId) {
        return paymentRepo.findByFinancialFileIdOrderByPaymentDateDesc(fileId);
    }

    @Transactional(readOnly = true)
    public Payment requirePayment(Long id) {
        return paymentRepo.findById(id).orElseThrow(() -> new NotFoundException("الدفعة غير موجودة"));
    }

    @Transactional(readOnly = true)
    public Installment requireInstallment(Long id) {
        return installmentRepo.findById(id).orElseThrow(() -> new NotFoundException("القسط غير موجود"));
    }

    // ==================== أدوات داخلية ====================

    /** يعيد حساب مسدَّد القسط من الدفعات المؤكدة المرتبطة به فقط. */
    private void applyToInstallment(Long installmentId) {
        if (installmentId == null) {
            return;
        }
        Installment inst = installmentRepo.findById(installmentId).orElse(null);
        if (inst == null || inst.getStatus() == Enums.InstallmentStatus.CANCELLED) {
            return;
        }
        BigDecimal paid = paymentRepo.findByInstallmentId(installmentId).stream()
                .filter(x -> x.getStatus() == Enums.PaymentStatus.CONFIRMED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        inst.setPaidAmount(paid);
        if (paid.compareTo(inst.getAmount()) >= 0) {
            inst.setStatus(Enums.InstallmentStatus.PAID);
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            inst.setStatus(Enums.InstallmentStatus.PARTIAL);
        } else {
            // لا سداد مؤكد: يعود للاستحقاق أو التأخر بحسب تاريخه
            inst.setStatus(inst.getDueDate() != null && inst.getDueDate().isBefore(LocalDate.now())
                    ? Enums.InstallmentStatus.OVERDUE
                    : Enums.InstallmentStatus.DUE);
        }
        installmentRepo.save(inst);
    }

    private void recompute(Payment p) {
        if (p.getFinancialFileId() == null) {
            return;
        }
        fileRepo.findById(p.getFinancialFileId()).ifPresent(financialService::recomputeStatus);
    }

    /** مطابقة حرفية عمداً: تسجيل دفعة يغيّر حالة الملف المالي فيُطلب الرمز المعتمد كما هو. */
    private static Enums.PaymentMethod parseMethod(String raw) {
        return EnumParser.requiredExact(Enums.PaymentMethod.class, raw,
                "طريقة السداد", "طريقة السداد مطلوبة");
    }

    private static String appendNote(String existing, String extra) {
        if (existing == null || existing.isBlank()) {
            return extra;
        }
        return existing + " | " + extra;
    }
}
