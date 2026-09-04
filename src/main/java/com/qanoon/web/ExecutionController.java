package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.PageResult;
import com.qanoon.dto.ExecutionDtos;
import com.qanoon.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** التنفيذ: أوامر التنفيذ الخمسة وبوابة الاستيفاء. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @GetMapping("/executions")
    public PageResult list(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return executionService.search(status, q, page, size);
    }

    @GetMapping("/executions/{id}")
    public ExecutionDtos.ExecutionDetail detail(@PathVariable Long id) {
        return executionService.detail(id);
    }

    @PutMapping("/executions/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody ExecutionDtos.ExecutionRequest req) {
        executionService.update(id, req);
        return ApiResponse.ok("تم حفظ التعديلات", executionService.detail(id));
    }

    /** يجوز إصدار أكثر من أمر ساري في الوقت نفسه. */
    @PostMapping("/executions/{id}/orders")
    public ApiResponse addOrder(@PathVariable Long id, @RequestBody ExecutionDtos.OrderRequest req) {
        var o = executionService.addOrder(id, req);
        return ApiResponse.ok("تم إصدار الأمر: " + o.orderTypeLabel(), executionService.detail(id));
    }

    @PostMapping("/execution-orders/{id}/cancel")
    public ApiResponse cancelOrder(@PathVariable Long id, @RequestBody ExecutionDtos.CancelRequest req) {
        var o = executionService.cancelOrder(id, req == null ? null : req.reason());
        return ApiResponse.ok("تم إلغاء الأمر: " + o.orderTypeLabel(), null);
    }

    @PostMapping("/executions/{id}/payments")
    public ApiResponse addPayment(@PathVariable Long id,
                                  @RequestBody ExecutionDtos.ExecutionPaymentRequest req) {
        executionService.addPayment(id, req);
        return ApiResponse.ok("تم تسجيل الدفعة", executionService.detail(id));
    }

    @GetMapping("/executions/{id}/satisfaction-check")
    public ExecutionDtos.SatisfactionCheck satisfactionCheck(@PathVariable Long id) {
        return executionService.satisfactionCheck(id);
    }

    /** الاستيفاء: إلغاء كل الأوامر السارية، شهادة مرقّمة، إغلاق وأرشفة — في معاملة واحدة. */
    @PostMapping("/executions/{id}/satisfy")
    public ApiResponse satisfy(@PathVariable Long id) {
        ExecutionDtos.SatisfyResult r = executionService.satisfy(id);
        return ApiResponse.ok("اكتمل الاستيفاء وأُغلق الملف — شهادة رقم " + r.certificateNumber(), r);
    }

    @GetMapping("/executions/{id}/certificate")
    public ExecutionDtos.CertificateDto certificate(@PathVariable Long id) {
        return executionService.certificate(id);
    }
}
