package com.qanoon.service;

import com.qanoon.domain.Enums;
import com.qanoon.domain.Permission;
import com.qanoon.domain.User;
import com.qanoon.repo.ConsultationRepository;
import com.qanoon.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * محرك التوزيع الذكي للاستشارات.
 *
 * <p>الدرجة من 100:
 * <ul>
 *   <li><b>تخصص مطابق — 50</b>: درجة كاملة إن طابق تخصص المستشار تخصص الطلب (تجاهل حالة الأحرف والمسافات)، وإلا صفر.</li>
 *   <li><b>انخفاض الحمل — 30</b>: 30 × (1 − الحمل الحالي ÷ أعلى حمل بين المرشحين)، و30 للجميع إن كان أعلى حمل = 0.</li>
 *   <li><b>الخبرة والأقدمية — 20</b>: 20 × (سنوات الخدمة ÷ أعلى سنوات بين المرشحين) بحد أقصى 20، و0 للجميع إن كان أعلى سنوات = 0.</li>
 * </ul>
 * عند التعادل في الدرجة يُقدَّم الأقل حملاً. وغياب المتخصص لا يوقف التوزيع: أعلى مرشح متاح يأخذها.
 */
@Service
@RequiredArgsConstructor
public class AssignmentEngine {

    /** وزن مطابقة التخصص */
    public static final double W_SPECIALIZATION = 50.0;
    /** وزن انخفاض الحمل */
    public static final double W_LOAD = 30.0;
    /** وزن الخبرة والأقدمية */
    public static final double W_SENIORITY = 20.0;

    /** الحالات التي لا تُحتسب ضمن الحمل النشط */
    public static final List<Enums.ConsultStatus> CLOSED_STATUSES = List.of(
            Enums.ConsultStatus.SIGNED,
            Enums.ConsultStatus.SENT,
            Enums.ConsultStatus.ARCHIVED);

    private static final double DAYS_PER_YEAR = 365.25;

    private final UserRepository userRepository;
    private final ConsultationRepository consultationRepository;

    /** مرشّح للإسناد مع تفصيل درجاته وسببها بالعربية. */
    public record Candidate(Long userId, String fullName, double score, double specializationScore,
                            double loadScore, double seniorityScore, long activeCount, double years, String reason) {
    }

    /**
     * المرشحون: المستخدمون النشطون الذين يملكون صلاحية إدارة الاستشارات
     * (المستشارون وكبار المستشارين).
     */
    @Transactional(readOnly = true)
    public List<User> candidates() {
        List<User> out = new ArrayList<>();
        for (User u : userRepository.findByActiveTrue()) {
            if (!u.has(Permission.CONSULT_MANAGE)) {
                continue;
            }
            // المدير يملك كل الصلاحيات بحكم إشرافه لا لأنه يكتب الآراء،
            // فيُستبعد من التوزيع التلقائي — والإسناد اليدوي إليه يبقى متاحاً.
            if (u.has(Permission.USERS_MANAGE)) {
                continue;
            }
            out.add(u);
        }
        return out;
    }

    /** عدد الاستشارات النشطة للمستشار (ليست موقّعة ولا مرسلة ولا مؤرشفة). */
    @Transactional(readOnly = true)
    public long activeCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return consultationRepository.findByConsultant_IdAndStatusNotIn(userId, CLOSED_STATUSES).size();
    }

    /** ترتيب المرشحين تنازلياً بالدرجة، وعند التعادل يُقدَّم الأقل حملاً. */
    @Transactional(readOnly = true)
    public List<Candidate> rank(String specialization) {
        List<User> users = candidates();
        if (users.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        Map<Long, Long> loads = new LinkedHashMap<>();
        Map<Long, Double> seniority = new LinkedHashMap<>();
        long maxActive = 0L;
        double maxYears = 0.0;

        for (User u : users) {
            long active = activeCount(u.getId());
            double years = yearsSince(u.getJoinedAt(), today);
            loads.put(u.getId(), active);
            seniority.put(u.getId(), years);
            if (active > maxActive) {
                maxActive = active;
            }
            if (years > maxYears) {
                maxYears = years;
            }
        }

        String wanted = normalize(specialization);
        List<Candidate> out = new ArrayList<>();
        for (User u : users) {
            long active = loads.get(u.getId());
            double years = seniority.get(u.getId());

            double specializationScore = !wanted.isEmpty() && wanted.equals(normalize(u.getSpecialization()))
                    ? W_SPECIALIZATION : 0.0;
            double loadScore = maxActive == 0L
                    ? W_LOAD
                    : W_LOAD * (1.0 - ((double) active / (double) maxActive));
            double seniorityScore = maxYears <= 0.0
                    ? 0.0
                    : Math.min(W_SENIORITY, W_SENIORITY * (years / maxYears));

            specializationScore = round1(specializationScore);
            loadScore = round1(loadScore);
            seniorityScore = round1(seniorityScore);
            double score = round1(specializationScore + loadScore + seniorityScore);

            out.add(new Candidate(
                    u.getId(),
                    u.getFullName(),
                    score,
                    specializationScore,
                    loadScore,
                    seniorityScore,
                    active,
                    round1(years),
                    buildReason(specializationScore, loadScore, seniorityScore, score)));
        }

        Comparator<Candidate> byScoreDesc =
                Comparator.<Candidate>comparingDouble(Candidate::score).reversed();
        Comparator<Candidate> byLoadAsc =
                Comparator.<Candidate>comparingLong(Candidate::activeCount);
        Comparator<Candidate> byName = Comparator.comparing(
                Candidate::fullName, Comparator.nullsLast(Comparator.<String>naturalOrder()));
        out.sort(byScoreDesc.thenComparing(byLoadAsc).thenComparing(byName));
        return out;
    }

    /** أعلى مرشح بالدرجة، أو {@code null} إن لم يوجد أي مرشح. */
    @Transactional(readOnly = true)
    public Candidate best(String specialization) {
        List<Candidate> ranked = rank(specialization);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** سبب الإسناد بالعربية: "إسناد تلقائي: تخصص مطابق (50.0) + حمل منخفض (24.0) + أقدمية (12.0) = 86.0" */
    private String buildReason(double specializationScore, double loadScore, double seniorityScore, double score) {
        String spec = specializationScore > 0.0
                ? "تخصص مطابق (" + fmt(specializationScore) + ")"
                : "تخصص غير مطابق (" + fmt(specializationScore) + ")";
        return "إسناد تلقائي: " + spec
                + " + حمل منخفض (" + fmt(loadScore) + ")"
                + " + أقدمية (" + fmt(seniorityScore) + ")"
                + " = " + fmt(score);
    }

    /** سنوات الخدمة منذ تاريخ الالتحاق، و0 إن كان التاريخ فارغاً أو مستقبلياً. */
    public double yearsSince(LocalDate joinedAt, LocalDate today) {
        if (joinedAt == null || today == null) {
            return 0.0;
        }
        long days = ChronoUnit.DAYS.between(joinedAt, today);
        if (days <= 0L) {
            return 0.0;
        }
        return days / DAYS_PER_YEAR;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
