package com.qanoon.domain;

/**
 * كل التعدادات (Enums) المستخدمة في النظام مع أسمائها العربية.
 */
public final class Enums {
    private Enums() {}

    public interface Labeled { String label(); }

    /** حالة الملف المالي – تُشتق آلياً من الدفعات المؤكدة */
    public enum FileStatus implements Labeled {
        OPEN("مفتوح"), CONTACTED("قيد التواصل"), WARNED("تم الإنذار"), INSTALLMENT("خطة تقسيط"),
        PARTIALLY_PAID("سداد جزئي"), PAID("مسدد بالكامل"), CLOSED_OPINION("مغلق برأي قانوني"),
        EXEMPTED("إعفاء"), ESCALATED("مصعّد إلى قضية"), ARCHIVED("مؤرشف");
        private final String l; FileStatus(String l){this.l=l;} public String label(){return l;}
    }

    /** وجهات إنهاء الملف المالي الخمس */
    public enum ClosureType implements Labeled {
        FULL_PAYMENT("سداد كامل"), INSTALLMENT("سداد بالتقسيط"), LEGAL_OPINION("رأي قانوني بالإغلاق"),
        EXEMPTION("إعفاء"), ESCALATION("تصعيد إلى قضية");
        private final String l; ClosureType(String l){this.l=l;} public String label(){return l;}
    }

    public enum CommType implements Labeled {
        CALL("اتصال هاتفي"), SMS("رسالة نصية"), EMAIL("بريد إلكتروني"), WARNING("إنذار رسمي"),
        MEETING("اجتماع"), LETTER("خطاب");
        private final String l; CommType(String l){this.l=l;} public String label(){return l;}
    }

    public enum PaymentMethod implements Labeled {
        CASH("نقداً"), CHEQUE("شيك"), CARD("بطاقة"), TRANSFER("تحويل بنكي");
        private final String l; PaymentMethod(String l){this.l=l;} public String label(){return l;}
    }

    public enum PaymentStatus implements Labeled {
        PENDING("بانتظار التأكيد"), CONFIRMED("مؤكدة"), BOUNCED("مرتجعة"), CANCELLED("ملغاة");
        private final String l; PaymentStatus(String l){this.l=l;} public String label(){return l;}
    }

    public enum InstallmentStatus implements Labeled {
        DUE("مستحق"), PARTIAL("مسدد جزئياً"), PAID("مسدد"), OVERDUE("متأخر"), CANCELLED("ملغى");
        private final String l; InstallmentStatus(String l){this.l=l;} public String label(){return l;}
    }

    public enum CaseStatus implements Labeled {
        OPEN("مفتوحة"), IN_PROGRESS("قيد النظر"), JUDGED("صدر الحكم"), APPEALED("مستأنفة"),
        TRANSFERRED("محوّلة للتنفيذ"), CLOSED("مغلقة");
        private final String l; CaseStatus(String l){this.l=l;} public String label(){return l;}
    }

    public enum CaseType implements Labeled {
        CIVIL("مدنية"), COMMERCIAL("تجارية"), LABOR("عمالية"), CRIMINAL("جزائية"),
        FAMILY("أحوال شخصية"), REAL_ESTATE("عقارية"), ADMINISTRATIVE("إدارية");
        private final String l; CaseType(String l){this.l=l;} public String label(){return l;}
    }

    public enum JudgmentFor implements Labeled {
        CLIENT("لصالح الموكل"), OPPONENT("لصالح الخصم"), PARTIAL("جزئي");
        private final String l; JudgmentFor(String l){this.l=l;} public String label(){return l;}
    }

    public enum AppealStatus implements Labeled {
        PENDING("قيد النظر"), DECIDED("تم الفصل فيه"), WITHDRAWN("مسحوب");
        private final String l; AppealStatus(String l){this.l=l;} public String label(){return l;}
    }

    public enum AppealBy implements Labeled {
        CLIENT("الموكل"), OPPONENT("الخصم");
        private final String l; AppealBy(String l){this.l=l;} public String label(){return l;}
    }

    public enum HearingType implements Labeled {
        FIRST("أولى"), PLEADING("مرافعة"), EVIDENCE("إثبات"), EXPERT("خبرة"), JUDGMENT("نطق بالحكم"), OTHER("أخرى");
        private final String l; HearingType(String l){this.l=l;} public String label(){return l;}
    }

    public enum ExecStatus implements Labeled {
        OPEN("مفتوح"), IN_PROGRESS("قيد التنفيذ"), SATISFIED("تم الاستيفاء"), CLOSED("مغلق");
        private final String l; ExecStatus(String l){this.l=l;} public String label(){return l;}
    }

    /** أوامر التنفيذ الخمسة */
    public enum OrderType implements Labeled {
        BANK_FREEZE("حجز بنكي"), TRAVEL_BAN("منع سفر"), ARREST("ضبط وإحضار"),
        PROPERTY_SEIZURE("حجز عقاري"), SALARY_SEIZURE("حجز راتب");
        private final String l; OrderType(String l){this.l=l;} public String label(){return l;}
    }

    public enum OrderStatus implements Labeled {
        ACTIVE("ساري"), EXECUTED("منفّذ"), CANCELLED("ملغى");
        private final String l; OrderStatus(String l){this.l=l;} public String label(){return l;}
    }

    /** مراحل الاستشارة السبع + الإعادة بملاحظات */
    public enum ConsultStatus implements Labeled {
        RECEIVED("استلام"), STUDYING("دراسة"), UNDER_REVIEW("مراجعة"), RETURNED("معادة بملاحظات"),
        APPROVED("اعتماد"), SIGNED("توقيع"), SENT("إرسال"), ARCHIVED("أرشفة");
        private final String l; ConsultStatus(String l){this.l=l;} public String label(){return l;}
    }

    public enum Priority implements Labeled {
        LOW("منخفضة"), NORMAL("عادية"), HIGH("عالية"), URGENT("عاجلة");
        private final String l; Priority(String l){this.l=l;} public String label(){return l;}
    }

    public enum ArchiveType implements Labeled {
        JUDGMENT("حكم قضائي"), OPINION("رأي قانوني"), TEMPLATE("نموذج"), REFERENCE("مرجع"),
        COMMENTARY("تعليق"), FINANCIAL_FILE("ملف مالي"), CASE("قضية"), EXECUTION("ملف تنفيذ"), CONSULTATION("استشارة");
        private final String l; ArchiveType(String l){this.l=l;} public String label(){return l;}
    }

    public enum PartyType implements Labeled {
        INDIVIDUAL("فرد"), COMPANY("شركة"), GOVERNMENT("جهة حكومية");
        private final String l; PartyType(String l){this.l=l;} public String label(){return l;}
    }

    public enum NotificationType implements Labeled {
        INFO("معلومة"), WARNING("تنبيه"), DEADLINE("موعد نهائي"), TASK("مهمة");
        private final String l; NotificationType(String l){this.l=l;} public String label(){return l;}
    }
}
