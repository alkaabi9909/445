package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.common.ExcelExportService;
import com.qanoon.common.PageResult;
import com.qanoon.domain.ArchiveItem;
import com.qanoon.domain.SavedSearch;
import com.qanoon.service.ArchiveService;
import com.qanoon.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** الأرشيف الشامل، البحث المتقدم، والبحوث المحفوظة. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;
    private final SearchService searchService;
    private final ExcelExportService excel;

    @GetMapping("/archive")
    public PageResult search(@RequestParam(required = false) String q,
                             @RequestParam(required = false) String type,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        return PageResult.of(archiveService.search(q, type, category, from, to, page, size));
    }

    @GetMapping("/archive/{id}")
    public ArchiveItem get(@PathVariable Long id) {
        return archiveService.get(id);
    }

    @PostMapping("/archive")
    public ApiResponse create(@RequestBody ArchiveItem in) {
        return ApiResponse.ok("تمت إضافة العنصر للأرشيف", archiveService.create(in));
    }

    @PutMapping("/archive/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody ArchiveItem in) {
        return ApiResponse.ok("تم حفظ التعديلات", archiveService.update(id, in));
    }

    @DeleteMapping("/archive/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        archiveService.delete(id);
        return ApiResponse.ok("تم حذف العنصر");
    }

    @GetMapping("/archive/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var rows = new ArrayList<List<Object>>();
        for (ArchiveItem a : archiveService.search(q, type, category, from, to, 0, 5000).getContent()) {
            rows.add(archiveService.exportRow(a));
        }
        byte[] data = excel.export("الأرشيف", archiveService.exportHeaders(), rows);
        return AuditController.download(data, "الأرشيف.xlsx");
    }

    /** بحث موحّد عبر الأرشيف والمواد القانونية. */
    @GetMapping("/search")
    public List<SearchService.Hit> searchAll(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "50") int limit) {
        return searchService.searchAll(q, from, to, limit);
    }

    @GetMapping("/searches")
    public SearchService.SavedSearches searches() {
        return searchService.mine();
    }

    @PostMapping("/searches")
    public ApiResponse saveSearch(@RequestBody Map<String, String> body) {
        SavedSearch s = searchService.save(body.get("name"), body.get("queryText"), body.get("filtersJson"));
        return ApiResponse.ok(s.isSaved() ? "تم حفظ البحث" : "تم تسجيل البحث", s);
    }

    @DeleteMapping("/searches/{id}")
    public ApiResponse deleteSearch(@PathVariable Long id) {
        searchService.delete(id);
        return ApiResponse.ok("تم حذف البحث");
    }
}
