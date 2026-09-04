package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.PageResult;
import com.qanoon.dto.CaseDtos;
import com.qanoon.service.CaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** المسار الثاني: القضايا، الجلسات، الأحكام، الاستئناف، وبوابة التحويل للتنفيذ. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @GetMapping("/cases")
    public PageResult list(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return caseService.list(status, q, page, size);
    }

    @GetMapping("/cases/{id}")
    public CaseDtos.CaseDetail detail(@PathVariable Long id) {
        return caseService.detail(id);
    }

    @PostMapping("/cases")
    public ApiResponse create(@RequestBody CaseDtos.CaseRequest req) {
        var c = caseService.create(req);
        return ApiResponse.ok("تم فتح القضية برقم " + c.getCaseNumber(), caseService.detail(c.getId()));
    }

    @PutMapping("/cases/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody CaseDtos.CaseRequest req) {
        caseService.update(id, req);
        return ApiResponse.ok("تم حفظ التعديلات", caseService.detail(id));
    }

    @PostMapping("/cases/{id}/assign")
    public ApiResponse assign(@PathVariable Long id, @RequestBody CaseDtos.AssignRequest req) {
        caseService.assign(id, req);
        return ApiResponse.ok("تم إسناد القضية وتوثيق سبب الإسناد", caseService.detail(id));
    }

    @PostMapping("/cases/{id}/hearings")
    public ApiResponse addHearing(@PathVariable Long id, @RequestBody CaseDtos.HearingRequest req) {
        caseService.addHearing(id, req);
        return ApiResponse.ok("تم تسجيل الجلسة", caseService.detail(id));
    }

    @PutMapping("/hearings/{id}")
    public ApiResponse updateHearing(@PathVariable Long id, @RequestBody CaseDtos.HearingRequest req) {
        var h = caseService.updateHearing(id, req);
        return ApiResponse.ok("تم تحديث الجلسة", caseService.detail(h.getCaseId()));
    }

    /** توثيق الحكم — يحسب النظام أجل الطعن آلياً. */
    @PostMapping("/cases/{id}/judgment")
    public ApiResponse recordJudgment(@PathVariable Long id, @RequestBody CaseDtos.JudgmentRequest req) {
        var c = caseService.recordJudgment(id, req);
        String msg = "تم توثيق الحكم";
        if (c.getAppealDeadline() != null) {
            msg += " — أجل الطعن ينتهي في " + c.getAppealDeadline()
                    + "، والتحويل للتنفيذ لا يجوز إلا من اليوم التالي";
        }
        return ApiResponse.ok(msg, caseService.detail(id));
    }

    @PostMapping("/cases/{id}/appeals")
    public ApiResponse addAppeal(@PathVariable Long id, @RequestBody CaseDtos.AppealRequest req) {
        caseService.addAppeal(id, req);
        return ApiResponse.ok("تم تسجيل الاستئناف — التحويل للتنفيذ ممنوع حتى الفصل فيه",
                caseService.detail(id));
    }

    @PostMapping("/appeals/{id}/decide")
    public ApiResponse decideAppeal(@PathVariable Long id,
                                    @RequestBody CaseDtos.AppealDecisionRequest req) {
        var a = caseService.decideAppeal(id, req);
        return ApiResponse.ok("تم تسجيل الفصل في الاستئناف", caseService.detail(a.getCaseId()));
    }

    /** الشروط الستة — تُعرض كاملة حتى لو مُنع التحويل. */
    @GetMapping("/cases/{id}/transfer-check")
    public CaseService.TransferCheck transferCheck(@PathVariable Long id) {
        return caseService.checkTransfer(id);
    }

    /** التحويل — يُعاد فحص الشروط الستة على الخادم قبل التنفيذ. */
    @PostMapping("/cases/{id}/transfer")
    public ApiResponse transfer(@PathVariable Long id) {
        CaseDtos.TransferResult r = caseService.transfer(id);
        return ApiResponse.ok("تم تحويل القضية إلى ملف تنفيذ رقم " + r.executionNumber(), r);
    }

    @PostMapping("/cases/{id}/close")
    public ApiResponse close(@PathVariable Long id, @RequestBody(required = false) CaseDtos.CloseRequest req) {
        caseService.close(id, req == null ? null : req.note());
        return ApiResponse.ok("تم إغلاق القضية وأرشفتها");
    }
}
