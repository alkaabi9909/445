package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.PageResult;
import com.qanoon.dto.ConsultationDtos;
import com.qanoon.service.ConsultationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** المسار الثالث: الاستشارات القانونية بالتوزيع الذكي والمراجعة المزدوجة. */
@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @GetMapping
    public PageResult list(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String q,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return consultationService.list(status, q, page, size);
    }

    /** ترتيب المرشحين بالدرجات قبل الإسناد: تخصص ٥٠ + حمل ٣٠ + أقدمية ٢٠. */
    @GetMapping("/suggest")
    public List<ConsultationDtos.CandidateView> suggest(@RequestParam(required = false) String specialization) {
        return consultationService.suggest(specialization);
    }

    @GetMapping("/{id}")
    public ConsultationDtos.ConsultationDetail detail(@PathVariable Long id) {
        return consultationService.detail(id);
    }

    @PostMapping
    public ApiResponse create(@RequestBody ConsultationDtos.CreateRequest req) {
        var c = consultationService.create(req);
        return ApiResponse.ok("تم تسجيل الاستشارة برقم " + c.getConsultationNumber(),
                consultationService.detail(c.getId()));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse assign(@PathVariable Long id,
                              @RequestBody(required = false) ConsultationDtos.AssignRequest req) {
        var c = consultationService.assign(id, req == null ? null : req.consultantId());
        return ApiResponse.ok("تم الإسناد — " + c.getAssignmentReason(), consultationService.detail(id));
    }

    @PostMapping("/{id}/opinion")
    public ApiResponse saveOpinion(@PathVariable Long id,
                                   @RequestBody ConsultationDtos.OpinionRequest req) {
        consultationService.saveOpinion(id, req == null ? null : req.opinionText());
        return ApiResponse.ok("تم حفظ الرأي القانوني", consultationService.detail(id));
    }

    @PostMapping("/{id}/submit-review")
    public ApiResponse submitForReview(@PathVariable Long id) {
        consultationService.submitForReview(id);
        return ApiResponse.ok("أُرسل الرأي للمراجعة — المراجعة الثانية إلزامية من مستشار آخر",
                consultationService.detail(id));
    }

    @PostMapping("/{id}/review")
    public ApiResponse review(@PathVariable Long id, @RequestBody ConsultationDtos.ReviewRequest req) {
        boolean approved = req != null && Boolean.TRUE.equals(req.approved());
        consultationService.review(id, approved, req == null ? null : req.notes());
        return ApiResponse.ok(approved ? "تم اعتماد الرأي" : "أُعيد الرأي للمستشار مع الملاحظات",
                consultationService.detail(id));
    }

    @PostMapping("/{id}/sign")
    public ApiResponse sign(@PathVariable Long id) {
        consultationService.sign(id);
        return ApiResponse.ok("تم التوقيع النهائي — الرأي مقفل الآن ولا يُعدَّل ولو من المدير",
                consultationService.detail(id));
    }

    @PostMapping("/{id}/send")
    public ApiResponse send(@PathVariable Long id, @RequestBody ConsultationDtos.SendRequest req) {
        consultationService.send(id, req == null ? null : req.sentTo());
        return ApiResponse.ok("تم إرسال الرأي", consultationService.detail(id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse archive(@PathVariable Long id) {
        consultationService.archive(id);
        return ApiResponse.ok("تمت أرشفة الاستشارة", consultationService.detail(id));
    }

    /** التصحيح لا يكون بتعديل الرأي الموقّع، بل برأي جديد يشير إليه. */
    @PostMapping("/{id}/correction")
    public ApiResponse correction(@PathVariable Long id) {
        ConsultationDtos.CorrectionResult r = consultationService.createCorrection(id);
        return ApiResponse.ok("أُنشئ رأي تصحيحي برقم " + r.consultationNumber(), r);
    }
}
