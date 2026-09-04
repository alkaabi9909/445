package com.qanoon.common;

/** لا صلاحية لتنفيذ العملية — تُترجم إلى استجابة 403 برسالة عربية. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String messageAr) {
        super(messageAr);
    }
}
