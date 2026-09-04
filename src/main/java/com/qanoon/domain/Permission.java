package com.qanoon.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** رموز الصلاحيات الدقيقة المستخدمة في النظام مع وصفها العربي. */
public final class Permission {
    private Permission() {}

    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";
    public static final String DASHBOARD_ALL = "DASHBOARD_ALL";
    public static final String CLIENTS_VIEW = "CLIENTS_VIEW";
    public static final String CLIENTS_MANAGE = "CLIENTS_MANAGE";
    public static final String FINANCIAL_VIEW = "FINANCIAL_VIEW";
    public static final String FINANCIAL_MANAGE = "FINANCIAL_MANAGE";
    public static final String FINANCIAL_VIEW_ALL = "FINANCIAL_VIEW_ALL";
    public static final String PAYMENT_CONFIRM = "PAYMENT_CONFIRM";
    public static final String EXEMPTION_APPROVE = "EXEMPTION_APPROVE";
    public static final String CASE_VIEW = "CASE_VIEW";
    public static final String CASE_MANAGE = "CASE_MANAGE";
    public static final String CASE_VIEW_ALL = "CASE_VIEW_ALL";
    public static final String CASE_ASSIGN = "CASE_ASSIGN";
    public static final String CASE_TRANSFER = "CASE_TRANSFER";
    public static final String EXECUTION_VIEW = "EXECUTION_VIEW";
    public static final String EXECUTION_MANAGE = "EXECUTION_MANAGE";
    public static final String EXECUTION_VIEW_ALL = "EXECUTION_VIEW_ALL";
    public static final String CONSULT_VIEW = "CONSULT_VIEW";
    public static final String CONSULT_MANAGE = "CONSULT_MANAGE";
    public static final String CONSULT_VIEW_ALL = "CONSULT_VIEW_ALL";
    public static final String CONSULT_ASSIGN = "CONSULT_ASSIGN";
    public static final String CONSULT_REVIEW = "CONSULT_REVIEW";
    public static final String CONSULT_SIGN = "CONSULT_SIGN";
    public static final String ARCHIVE_VIEW = "ARCHIVE_VIEW";
    public static final String ARCHIVE_MANAGE = "ARCHIVE_MANAGE";
    public static final String REPORTS_VIEW = "REPORTS_VIEW";
    public static final String USERS_MANAGE = "USERS_MANAGE";
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String BACKUP_RUN = "BACKUP_RUN";

    public static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(DASHBOARD_VIEW, "عرض لوحة المعلومات");
        LABELS.put(DASHBOARD_ALL, "لوحة معلومات المكتب كاملة");
        LABELS.put(CLIENTS_VIEW, "عرض الموكلين والخصوم");
        LABELS.put(CLIENTS_MANAGE, "إدارة الموكلين والخصوم");
        LABELS.put(FINANCIAL_VIEW, "عرض الملفات المالية المسندة");
        LABELS.put(FINANCIAL_MANAGE, "إدارة الملفات المالية");
        LABELS.put(FINANCIAL_VIEW_ALL, "عرض كل الملفات المالية");
        LABELS.put(PAYMENT_CONFIRM, "تأكيد الدفعات");
        LABELS.put(EXEMPTION_APPROVE, "الموافقة على الإعفاء");
        LABELS.put(CASE_VIEW, "عرض القضايا المسندة");
        LABELS.put(CASE_MANAGE, "إدارة القضايا");
        LABELS.put(CASE_VIEW_ALL, "عرض كل القضايا");
        LABELS.put(CASE_ASSIGN, "إسناد القضايا للمحامين");
        LABELS.put(CASE_TRANSFER, "تحويل القضية للتنفيذ");
        LABELS.put(EXECUTION_VIEW, "عرض ملفات التنفيذ المسندة");
        LABELS.put(EXECUTION_MANAGE, "إدارة ملفات التنفيذ");
        LABELS.put(EXECUTION_VIEW_ALL, "عرض كل ملفات التنفيذ");
        LABELS.put(CONSULT_VIEW, "عرض الاستشارات الخاصة");
        LABELS.put(CONSULT_MANAGE, "إدارة الاستشارات");
        LABELS.put(CONSULT_VIEW_ALL, "عرض كل الاستشارات");
        LABELS.put(CONSULT_ASSIGN, "إسناد الاستشارات");
        LABELS.put(CONSULT_REVIEW, "مراجعة الآراء القانونية");
        LABELS.put(CONSULT_SIGN, "التوقيع النهائي على الآراء");
        LABELS.put(ARCHIVE_VIEW, "عرض الأرشيف والبحث");
        LABELS.put(ARCHIVE_MANAGE, "إدارة الأرشيف والقوانين");
        LABELS.put(REPORTS_VIEW, "عرض التقارير");
        LABELS.put(USERS_MANAGE, "إدارة المستخدمين والأدوار");
        LABELS.put(SETTINGS_MANAGE, "إدارة الإعدادات");
        LABELS.put(AUDIT_VIEW, "عرض سجل النشاطات");
        LABELS.put(BACKUP_RUN, "تشغيل النسخ الاحتياطي");
    }
}
