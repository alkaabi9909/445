package com.qanoon.common;

import com.qanoon.domain.Enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * تحويل نصوص الطلبات إلى قيم تعداد بسلوك موحّد في كل النظام.
 *
 * <p>القاعدة الأولى — الرفض: القيمة المجهولة تُرفض دائماً بـ
 * {@link BusinessException} أي برمز 400 ورسالة عربية تسرد القيم المتاحة.
 * لا تُتجاهل صامتة (فتبدو التصفية وكأنها طُبِّقت بينما تُعاد كل السجلات)
 * ولا تتسرّب كـ {@code IllegalArgumentException} فتصير خطأ خادم 500.</p>
 *
 * <p>القاعدة الثانية — حالة الأحرف: <b>القراءة تتسامح والكتابة لا تتسامح.</b>
 * مُرشّحات العرض تقبل {@code "open"} و{@code " OPEN "} سواء، لأن رفض بحث
 * بسبب حالة حرف إزعاج بلا فائدة. أما المسارات التي تُغيّر الحالة — تسجيل
 * دفعة، إنهاء ملف، إصدار أمر تنفيذ — فتُطابق الرمز حرفياً، لأن الخادم
 * يجب ألا يخمّن نيّة العميل في إجراء نهائي.</p>
 *
 * <p>المسافات المحيطة تُشذَّب في الحالتين.</p>
 *
 * <p>الاختيار بين الثلاثة:</p>
 * <ul>
 *   <li>{@link #optional} — مُرشّح قراءة، الفراغ يعني «بلا تصفية».</li>
 *   <li>{@link #optionalExact} — كتابة بحقل اختياري.</li>
 *   <li>{@link #requiredExact} — كتابة بحقل إلزامي.</li>
 * </ul>
 *
 * <p>لا يوجد «إلزامي متسامح» لأن كل حقل إلزامي في النظام يقع على مسار كتابة،
 * والكتابة لا تتسامح.</p>
 */
public final class EnumParser {

    private EnumParser() {}

    /**
     * مُرشّح قراءة اختياري: الفراغ يعني «بلا تصفية» فيُعاد {@code null}،
     * والقيمة المجهولة تُرفض. حالة الأحرف متسامحة.
     *
     * @param fieldAr اسم الحقل بالعربية كما يظهر في الرسالة
     */
    public static <E extends Enum<E>> E optional(Class<E> type, String value, String fieldAr) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parse(type, value, fieldAr, true);
    }

    /**
     * حقل كتابة اختياري بمطابقة حرفية: الفراغ يُعيد {@code null}،
     * وغيره يجب أن يطابق رمز التعداد كما هو — فلا يُقبل {@code "cash"}
     * بدل {@code "CASH"}.
     */
    public static <E extends Enum<E>> E optionalExact(Class<E> type, String value, String fieldAr) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parse(type, value, fieldAr, false);
    }

    /**
     * حقل كتابة إلزامي بمطابقة حرفية: الفراغ يُرفض برسالة المُستدعي،
     * والقيمة يجب أن تطابق رمز التعداد كما هو.
     *
     * @param missingMessageAr رسالة الرفض عند غياب القيمة
     */
    public static <E extends Enum<E>> E requiredExact(Class<E> type, String value,
                                                      String fieldAr, String missingMessageAr) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(missingMessageAr);
        }
        return parse(type, value, fieldAr, false);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value,
                                               String fieldAr, boolean normaliseCase) {
        String candidate = normaliseCase ? value.trim().toUpperCase(Locale.ROOT) : value.trim();
        try {
            return Enum.valueOf(type, candidate);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("قيمة غير معروفة لحقل " + fieldAr + ": " + value.trim()
                    + " — القيم المتاحة: " + available(type));
        }
    }

    /** القيم المتاحة بأوصافها العربية إن كان التعداد موسوماً، وإلا برموزها. */
    private static <E extends Enum<E>> String available(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(c -> c instanceof Enums.Labeled labeled ? labeled.label() : c.name())
                .collect(Collectors.joining("، "));
    }
}
