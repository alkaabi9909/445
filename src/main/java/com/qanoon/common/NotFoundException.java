package com.qanoon.common;

/** السجل المطلوب غير موجود — تُترجم إلى استجابة 404 برسالة عربية. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String messageAr) {
        super(messageAr);
    }
}
