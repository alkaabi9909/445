package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.EnumParser;
import com.qanoon.common.PageResult;
import com.qanoon.domain.Enums;
import com.qanoon.dto.FinancialDtos;
import com.qanoon.service.FinancialService;
import com.qanoon.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** المسار الأول: الملف المالي. */
@RestController
@RequestMapping("/api/financial")
@RequiredArgsConstructor
public class FinancialController {

    private final FinancialService financialService;
    private final PaymentService paymentService;

    @GetMapping
    public PageResult list(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return financialService.list(status, q, page, size);
    }

    @GetMapping("/{id}")
    public FinancialDtos.FinancialFileDetail detail(@PathVariable Long id) {
        return financialService.detail(id);
    }

    @PostMapping
    public ApiResponse create(@RequestBody FinancialDtos.FinancialFileRequest req) {
        var f = financialService.create(req);
        return ApiResponse.ok("تم فتح الملف المالي برقم " + f.getFileNumber(), financialService.detail(f.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody FinancialDtos.FinancialFileRequest req) {
        financialService.update(id, req);
        return ApiResponse.ok("تم حفظ التعديلات", financialService.detail(id));
    }

    @PostMapping("/{id}/communications")
    public ApiResponse addCommunication(@PathVariable Long id,
                                        @RequestBody FinancialDtos.CommunicationRequest req) {
        financialService.addCommunication(id, req);
        return ApiResponse.ok("تم تسجيل التواصل", financialService.detail(id));
    }

    @PostMapping("/{id}/plan")
    public ApiResponse createPlan(@PathVariable Long id,
                                  @RequestBody FinancialDtos.PaymentPlanRequest req) {
        financialService.createPlan(id, req);
        return ApiResponse.ok("تم إنشاء خطة التقسيط", financialService.detail(id));
    }

    @PostMapping("/{id}/payments")
    public ApiResponse addPayment(@PathVariable Long id,
                                  @RequestBody FinancialDtos.PaymentRequest req) {
        var p = paymentService.addPayment(id, req);
        return ApiResponse.ok("تم تسجيل الدفعة برقم إيصال " + p.getReceiptNumber()
                + " — بانتظار التأكيد", financialService.detail(id));
    }

    /** وجهات إنهاء الملف الخمس. */
    @PostMapping("/{id}/close")
    public ApiResponse close(@PathVariable Long id, @RequestBody FinancialDtos.CloseRequest req) {
        Enums.ClosureType type = parseClosure(req.closureType());
        FinancialDtos.CloseResult result = financialService.close(id, type, req.note(), req.approvedById());
        return ApiResponse.ok(closureMessage(type, result), result);
    }

    /** مطابقة حرفية عمداً: إنهاء الملف إجراء نهائي فيُطلب الرمز المعتمد كما هو. */
    private static Enums.ClosureType parseClosure(String raw) {
        return EnumParser.requiredExact(Enums.ClosureType.class, raw,
                "وجهة إنهاء الملف", "وجهة إنهاء الملف مطلوبة");
    }

    private static String closureMessage(Enums.ClosureType type, FinancialDtos.CloseResult result) {
        if (type == Enums.ClosureType.ESCALATION) {
            return "تم تصعيد الملف إلى قضية — نُقلت كل المرفقات وسجل التواصل إلى القضية الجديدة";
        }
        return "تم إنهاء الملف: " + type.label() + " وأُرشف بمستنداته";
    }
}
