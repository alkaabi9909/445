package com.qanoon.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * المعالج المركزي للأخطاء: يحوّل كل استثناء إلى JSON موحّد
 * على الصيغة {"error":"رسالة عربية","blockers":[...]} مع رمز الحالة المناسب.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- أخطاء النظام الخاصة ----------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message(ex.getMessage(), "لا يمكن تنفيذ العملية"));
        body.put("blockers", ex.getBlockers());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(message(ex.getMessage(), "السجل المطلوب غير موجود"), null));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body(message(ex.getMessage(), "لا تملك صلاحية تنفيذ هذه العملية"), null));
    }

    // ---------- الأمان ----------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("لا تملك صلاحية تنفيذ هذه العملية", null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(body("يجب تسجيل الدخول للمتابعة", null));
    }

    // ---------- التحقق من المدخلات ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> blockers = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String reason = fe.getDefaultMessage() == null ? "قيمة غير صالحة" : fe.getDefaultMessage();
            blockers.add(fe.getField() + ": " + reason);
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                blockers.add(ge.getDefaultMessage() == null ? "بيانات غير صالحة" : ge.getDefaultMessage()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "البيانات المدخلة غير صحيحة — يرجى مراجعة الحقول المطلوبة");
        body.put("blockers", blockers);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("قيمة غير صالحة للحقل: " + ex.getName(), null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("حقل مطلوب مفقود في الطلب: " + ex.getParameterName(), null));
    }

    /**
     * جزء مفقود في طلب متعدد الأجزاء — مثل رفع مرفق بلا ملف.
     * بدونه كان الخطأ يسقط في المعالج العام فيعود 500 ويُسجَّل كخطأ نظام،
     * مع أنه خطأ في الطلب لا في الخادم.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("جزء مطلوب مفقود في الطلب: " + ex.getRequestPartName(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("تعذّرت قراءة بيانات الطلب — تحقق من صيغة البيانات المرسلة", null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("حجم الملف يتجاوز الحد المسموح (25 ميغابايت)", null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("مخالفة قيود قاعدة البيانات: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("تعذّر حفظ البيانات — قد تكون مكررة أو مرتبطة بسجلات أخرى", null));
    }

    // ---------- أخطاء البروتوكول ----------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body("المسار المطلوب غير موجود", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(body("طريقة الطلب غير مدعومة لهذا المسار", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(body(message(ex.getReason(), "تعذّر تنفيذ الطلب"), null));
    }

    // ---------- ما تبقّى ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex, HttpServletRequest request) {
        String path = request == null ? "-" : request.getRequestURI();
        log.error("خطأ غير متوقع أثناء معالجة الطلب [{}]", path, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("حدث خطأ غير متوقع في النظام — تم تسجيل الخطأ، يرجى المحاولة لاحقاً أو مراجعة مسؤول النظام", null));
    }

    // ---------- أدوات ----------

    private Map<String, Object> body(String error, List<String> blockers) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("error", error);
        if (blockers != null && !blockers.isEmpty()) {
            map.put("blockers", blockers);
        }
        return map;
    }

    private String message(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
