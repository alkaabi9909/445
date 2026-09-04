package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.dto.FinancialDtos;
import com.qanoon.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** تأكيد الدفعات وارتجاع الشيكات وإلغاء الأقساط. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments/{id}/confirm")
    public ApiResponse confirm(@PathVariable Long id) {
        var p = paymentService.confirm(id);
        return ApiResponse.ok("تم تأكيد الدفعة " + p.getReceiptNumber()
                + " — أُعيد احتساب حالة الملف تلقائياً", null);
    }

    @PostMapping("/payments/{id}/bounce")
    public ApiResponse bounce(@PathVariable Long id, @RequestBody FinancialDtos.ReasonRequest req) {
        var p = paymentService.markBounced(id, req == null ? null : req.reason());
        return ApiResponse.ok("سُجّل ارتجاع الدفعة " + p.getReceiptNumber()
                + " — أُعيدت الأقساط المرتبطة إلى الاستحقاق", null);
    }

    @PostMapping("/payments/{id}/cancel")
    public ApiResponse cancel(@PathVariable Long id, @RequestBody FinancialDtos.ReasonRequest req) {
        paymentService.cancel(id, req == null ? null : req.reason());
        return ApiResponse.ok("تم إلغاء الدفعة");
    }

    @PostMapping("/installments/{id}/cancel")
    public ApiResponse cancelInstallment(@PathVariable Long id,
                                         @RequestBody FinancialDtos.ReasonRequest req) {
        var i = paymentService.cancelInstallment(id, req == null ? null : req.reason());
        return ApiResponse.ok("تم إلغاء القسط رقم " + i.getSeq());
    }
}
