package com.qanoon.common;

/** الاستجابة الموحّدة لعمليات التعديل: نجاح + رسالة عربية + بيانات اختيارية. */
public record ApiResponse(boolean ok, String message, Object data) {

    public static ApiResponse ok(String messageAr) {
        return new ApiResponse(true, messageAr, null);
    }

    public static ApiResponse ok(String messageAr, Object data) {
        return new ApiResponse(true, messageAr, data);
    }
}
