package com.qanoon.web;

import com.qanoon.common.ApiResponse;
import com.qanoon.domain.LawArticle;
import com.qanoon.domain.LawChapter;
import com.qanoon.domain.LawCode;
import com.qanoon.service.LawService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** المكتبة القانونية: باب ← فصل ← مادة. */
@RestController
@RequestMapping("/api/laws")
@RequiredArgsConstructor
public class LawController {

    private final LawService lawService;

    @GetMapping
    public List<LawCode> codes() {
        return lawService.codes();
    }

    @GetMapping("/search")
    public List<LawService.SearchHit> search(@RequestParam(required = false) String q) {
        return lawService.search(q);
    }

    @GetMapping("/{id}/tree")
    public LawService.LawTree tree(@PathVariable Long id) {
        return lawService.tree(id);
    }

    @PostMapping
    public ApiResponse createCode(@RequestBody LawCode in) {
        return ApiResponse.ok("تمت إضافة التشريع", lawService.createCode(in));
    }

    @PostMapping("/{id}/chapters")
    public ApiResponse createChapter(@PathVariable Long id, @RequestBody LawChapter in) {
        return ApiResponse.ok("تمت الإضافة", lawService.createChapter(id, in));
    }

    @PostMapping("/{id}/articles")
    public ApiResponse createArticle(@PathVariable Long id, @RequestBody LawArticle in) {
        return ApiResponse.ok("تمت إضافة المادة", lawService.createArticle(id, in));
    }
}
