package com.qanoon.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * مخالفة لقاعدة من قواعد العمل — تُترجم إلى استجابة 400 برسالة عربية
 * وقائمة موانع اختيارية تُعرض للمستخدم سطراً سطراً.
 */
public class BusinessException extends RuntimeException {

    private final List<String> blockers;

    public BusinessException(String messageAr) {
        super(messageAr);
        this.blockers = Collections.emptyList();
    }

    public BusinessException(String messageAr, List<String> blockers) {
        super(messageAr);
        this.blockers = (blockers == null || blockers.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(blockers));
    }

    /** الموانع التفصيلية (قد تكون فارغة، ولا تكون null أبداً). */
    public List<String> getBlockers() {
        return blockers;
    }
}
