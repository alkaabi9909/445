package com.qanoon.common;

import com.qanoon.domain.SystemSetting;
import com.qanoon.repo.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** الإعدادات العامة: تُقرأ من القاعدة مع تخزين مؤقت، وتعود لقيم application.yml عند الغياب. */
@Service
@RequiredArgsConstructor
public class SettingService {

    public static final String OFFICE_NAME = "office.name";
    public static final String CURRENCY = "currency";
    public static final String APPEAL_DAYS = "appeal.days";
    public static final String APPEAL_ALERT_DAYS = "appeal.alert.days";
    public static final String HEARING_ALERT_DAYS = "hearing.alert.days";
    public static final String INSTALLMENT_ALERT_DAYS = "installment.alert.days";

    private final SystemSettingRepository repo;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Value("${qanoon.office-name:مكتب المحاماة والاستشارات القانونية}")
    private String defaultOfficeName;

    @Value("${qanoon.currency:AED}")
    private String defaultCurrency;

    @Value("${qanoon.appeal-days:30}")
    private int defaultAppealDays;

    @Value("${qanoon.appeal-alert-days:7}")
    private int defaultAppealAlertDays;

    @Value("${qanoon.hearing-alert-days:3}")
    private int defaultHearingAlertDays;

    @Value("${qanoon.installment-alert-days:3}")
    private int defaultInstallmentAlertDays;

    @Transactional(readOnly = true)
    public String get(String key, String def) {
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String value = repo.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
        if (value == null) {
            return def;
        }
        cache.put(key, value);
        return value;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        String v = get(key, String.valueOf(def));
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim()) || "نعم".equals(v.trim());
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> all() {
        return repo.findAll();
    }

    @Transactional
    public void set(String key, String value) {
        SystemSetting s = repo.findBySettingKey(key).orElseGet(() -> {
            SystemSetting n = new SystemSetting();
            n.setSettingKey(key);
            n.setNameAr(key);
            n.setValueType("TEXT");
            return n;
        });
        s.setSettingValue(value);
        repo.save(s);
        cache.remove(key);
    }

    /** يُستدعى بعد أي تعديل جماعي على الإعدادات. */
    public void clearCache() {
        cache.clear();
    }

    // ---------- اختصارات مستخدمة كثيراً ----------

    /** مدة الطعن القانونية بالأيام. */
    public int appealDays() {
        return getInt(APPEAL_DAYS, defaultAppealDays);
    }

    /** عدد أيام التنبيه المسبق قبل انتهاء أجل الطعن. */
    public int appealAlertDays() {
        return getInt(APPEAL_ALERT_DAYS, defaultAppealAlertDays);
    }

    public int hearingAlertDays() {
        return getInt(HEARING_ALERT_DAYS, defaultHearingAlertDays);
    }

    public int installmentAlertDays() {
        return getInt(INSTALLMENT_ALERT_DAYS, defaultInstallmentAlertDays);
    }

    public String currency() {
        return get(CURRENCY, defaultCurrency);
    }

    public String officeName() {
        return get(OFFICE_NAME, defaultOfficeName);
    }
}
