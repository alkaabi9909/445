package com.qanoon.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** نتيجة مُصفّحة موحّدة لكل الشاشات: العناصر + الإجمالي + رقم الصفحة + حجمها. */
public record PageResult(List<?> items, long total, int page, int size) {

    /** تحويل صفحة Spring Data إلى النتيجة الموحّدة. */
    public static PageResult of(Page<?> page) {
        if (page == null) {
            return new PageResult(List.of(), 0L, 0, 0);
        }
        return new PageResult(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
