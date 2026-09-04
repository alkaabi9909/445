package com.qanoon.config;

import com.qanoon.domain.*;
import com.qanoon.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * البيانات التجريبية للعرض — تعمل مرة واحدة فقط عندما تكون القاعدة فارغة.
 * تغطي كل الحالات المذكورة في المواصفة حتى يظهر النظام عاملاً من أول تشغيل.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String PASSWORD = "Qanoon@123";

    private final RoleRepository roleRepo;
    private final UserRepository userRepo;
    private final PartyRepository partyRepo;
    private final SystemSettingRepository settingRepo;
    private final FinancialFileRepository fileRepo;
    private final CommunicationRepository commRepo;
    private final PaymentPlanRepository planRepo;
    private final InstallmentRepository instRepo;
    private final PaymentRepository paymentRepo;
    private final LegalCaseRepository caseRepo;
    private final HearingRepository hearingRepo;
    private final AppealRepository appealRepo;
    private final ExecutionFileRepository execRepo;
    private final ExecutionOrderRepository orderRepo;
    private final ConsultationRepository consultRepo;
    private final ConsultationActionRepository actionRepo;
    private final LawCodeRepository lawCodeRepo;
    private final LawChapterRepository chapterRepo;
    private final LawArticleRepository articleRepo;
    private final ArchiveItemRepository archiveRepo;
    private final NumberSequenceRepository seqRepo;
    private final PasswordEncoder encoder;

    private final Map<String, Role> roles = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();
    private final List<Party> clients = new ArrayList<>();
    private final List<Party> debtors = new ArrayList<>();
    private final LocalDate today = LocalDate.now();
    private int fin = 0, cas = 0, exe = 0, con = 0, rcp = 0, arc = 0;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepo.count() > 0) {
            return;
        }
        log.info("قاعدة البيانات فارغة — يجري تحميل البيانات التجريبية...");
        seedRoles();
        seedUsers();
        seedSettings();
        seedParties();
        seedFinancial();
        seedCases();
        seedExecutions();
        seedConsultations();
        seedLaws();
        seedArchive();
        seedSequences();
        log.info("اكتمل التحميل: {} مستخدم، {} ملف مالي، {} قضية، {} ملف تنفيذ، {} استشارة، {} عنصر أرشيف",
                userRepo.count(), fileRepo.count(), caseRepo.count(),
                execRepo.count(), consultRepo.count(), archiveRepo.count());
        log.info("للدخول: admin / {}", PASSWORD);
    }

    // ==================== الأدوار ====================

    private void seedRoles() {
        role("MANAGER", "المدير", "رؤية كاملة على كل الملفات والتقارير والإعدادات", true,
                Permission.LABELS.keySet());

        role("LAWYER", "المحامي", "يرى القضايا والملفات المسندة إليه فقط", true, Set.of(
                Permission.DASHBOARD_VIEW, Permission.CLIENTS_VIEW, Permission.CLIENTS_MANAGE,
                Permission.FINANCIAL_VIEW, Permission.FINANCIAL_MANAGE, Permission.PAYMENT_CONFIRM,
                Permission.CASE_VIEW, Permission.CASE_MANAGE, Permission.CASE_TRANSFER,
                Permission.EXECUTION_VIEW, Permission.EXECUTION_MANAGE, Permission.ARCHIVE_VIEW));

        role("CONSULTANT", "المستشار القانوني", "يرى استشاراته فقط ولا يصل لشاشات الإعدادات", true, Set.of(
                Permission.DASHBOARD_VIEW, Permission.CONSULT_VIEW, Permission.CONSULT_MANAGE,
                Permission.ARCHIVE_VIEW, Permission.CLIENTS_VIEW));

        role("SENIOR_CONSULTANT", "كبير المستشارين", "صلاحية المراجعة والتوقيع النهائي على الآراء", true, Set.of(
                Permission.DASHBOARD_VIEW, Permission.CONSULT_VIEW, Permission.CONSULT_MANAGE,
                Permission.CONSULT_VIEW_ALL, Permission.CONSULT_ASSIGN, Permission.CONSULT_REVIEW,
                Permission.CONSULT_SIGN, Permission.ARCHIVE_VIEW, Permission.ARCHIVE_MANAGE,
                Permission.REPORTS_VIEW, Permission.CLIENTS_VIEW));

        role("SECRETARY", "سكرتير / موظف إدخال بيانات", "مثال على الأدوار المخصصة بصلاحيات دقيقة", false, Set.of(
                Permission.DASHBOARD_VIEW, Permission.CLIENTS_VIEW, Permission.CLIENTS_MANAGE,
                Permission.FINANCIAL_VIEW, Permission.FINANCIAL_VIEW_ALL,
                Permission.CASE_VIEW, Permission.CASE_VIEW_ALL, Permission.ARCHIVE_VIEW));
    }

    private void role(String code, String nameAr, String desc, boolean system, Collection<String> perms) {
        Role r = new Role();
        r.setCode(code);
        r.setNameAr(nameAr);
        r.setDescription(desc);
        r.setSystem(system);
        r.setPermissions(new LinkedHashSet<>(perms));
        roles.put(code, roleRepo.save(r));
    }

    // ==================== المستخدمون ====================

    private void seedUsers() {
        user("admin", "مدير المكتب", "MANAGER", null, LocalDate.of(2015, 1, 10), "050-1000001");
        user("lawyer1", "سالم المرزوقي", "LAWYER", "تجاري", LocalDate.of(2017, 3, 5), "050-1000002");
        user("lawyer2", "هند الشامسي", "LAWYER", "عمالي", LocalDate.of(2021, 9, 12), "050-1000003");
        user("consult1", "خالد النعيمي", "CONSULTANT", "تجاري", LocalDate.of(2019, 6, 1), "050-1000004");
        user("consult2", "عائشة البلوشي", "CONSULTANT", "عمالي", LocalDate.of(2024, 2, 17), "050-1000005");
        user("senior1", "د. يوسف الحمادي", "SENIOR_CONSULTANT", "تجاري", LocalDate.of(2012, 11, 3), "050-1000006");
        user("secretary1", "منى الظاهري", "SECRETARY", null, LocalDate.of(2022, 8, 21), "050-1000007");
    }

    private void user(String username, String fullName, String roleCode, String spec,
                      LocalDate joined, String phone) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setFullName(fullName);
        u.setEmail(username + "@qanoon.local");
        u.setPhone(phone);
        u.setRole(roles.get(roleCode));
        u.setSpecialization(spec);
        u.setJoinedAt(joined);
        u.setActive(true);
        users.put(username, userRepo.save(u));
    }

    // ==================== الإعدادات ====================

    private void seedSettings() {
        setting("office.name", "مكتب المحاماة والاستشارات القانونية", "اسم المكتب", "TEXT",
                "يظهر في الترويسة وفي المستندات المطبوعة", "عام");
        setting("currency", "AED", "العملة", "TEXT", "عملة المكتب — الدرهم الإماراتي", "عام");
        setting("appeal.days", "30", "مدة الطعن القانونية (يوم)", "NUMBER",
                "تُضاف لتاريخ الحكم لحساب أجل الطعن، وتتحكم في بوابة التحويل للتنفيذ", "القضايا");
        setting("appeal.alert.days", "7", "التنبيه قبل انتهاء أجل الطعن (يوم)", "NUMBER",
                "يبدأ التنبيه قبل هذه المدة ويستمر حتى اليوم الأخير من المهلة", "القضايا");
        setting("hearing.alert.days", "3", "التنبيه بالجلسات القادمة (يوم)", "NUMBER",
                "عدد الأيام التي تُعرض فيها الجلسة ضمن الملفات العاجلة", "القضايا");
        setting("installment.alert.days", "3", "التنبيه بالأقساط المستحقة (يوم)", "NUMBER",
                "عدد الأيام قبل استحقاق القسط لبدء التنبيه", "المالية");
        setting("password.min.length", "8", "أقل طول لكلمة المرور", "NUMBER",
                "مع اشتراط حرف كبير وحرف صغير ورقم ورمز", "الأمان");
        setting("max.failed.logins", "5", "عدد محاولات الدخول الفاشلة قبل القفل", "NUMBER",
                "يُقفل الحساب مؤقتاً بعد تجاوز هذا العدد", "الأمان");
        setting("lock.minutes", "15", "مدة قفل الحساب (دقيقة)", "NUMBER",
                "المدة التي يبقى فيها الحساب مقفلاً بعد المحاولات الفاشلة", "الأمان");
        setting("session.timeout.minutes", "30", "انتهاء الجلسة بعد عدم النشاط (دقيقة)", "NUMBER",
                "تُنهى الجلسة تلقائياً بعد هذه المدة من عدم النشاط", "الأمان");
    }

    private void setting(String key, String value, String nameAr, String type, String desc, String group) {
        SystemSetting s = new SystemSetting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        s.setNameAr(nameAr);
        s.setValueType(type);
        s.setDescription(desc);
        s.setSettingGroup(group);
        settingRepo.save(s);
    }

    // ==================== الأطراف ====================

    private void seedParties() {
        String[][] cl = {
                {"شركة الخليج للمقاولات ذ.م.م", "COMPANY", "CN-114520"},
                {"مؤسسة النخبة التجارية", "COMPANY", "CN-220841"},
                {"عبدالله راشد الكتبي", "INDIVIDUAL", "784-1985-1122334-1"},
                {"شركة الواحة العقارية", "COMPANY", "CN-330927"},
                {"مريم سعيد الحوسني", "INDIVIDUAL", "784-1990-2233445-2"},
                {"مجموعة الإمارات للتوريدات", "COMPANY", "CN-441038"},
                {"سلطان محمد القبيسي", "INDIVIDUAL", "784-1978-3344556-3"},
                {"شركة البحر الأزرق للشحن", "COMPANY", "CN-552149"},
        };
        String[][] db = {
                {"شركة الأفق للتجارة العامة", "COMPANY", "CN-660351"},
                {"ناصر علي المنصوري", "INDIVIDUAL", "784-1988-4455667-4"},
                {"مؤسسة الصقر للمقاولات", "COMPANY", "CN-770462"},
                {"فاطمة خميس السويدي", "INDIVIDUAL", "784-1992-5566778-5"},
                {"شركة النور للخدمات الفنية", "COMPANY", "CN-880573"},
                {"راشد سيف الدرعي", "INDIVIDUAL", "784-1983-6677889-6"},
                {"مجموعة الياسمين التجارية", "COMPANY", "CN-990684"},
                {"خالد عبيد الرميثي", "INDIVIDUAL", "784-1995-7788990-7"},
        };
        for (int i = 0; i < cl.length; i++) {
            clients.add(party(Party.Kind.CLIENT, cl[i][0], cl[i][1], cl[i][2], "04-500" + (1000 + i)));
        }
        for (int i = 0; i < db.length; i++) {
            debtors.add(party(Party.Kind.DEBTOR, db[i][0], db[i][1], db[i][2], "04-600" + (1000 + i)));
        }
    }

    private Party party(Party.Kind kind, String name, String type, String idNo, String phone) {
        Party p = new Party();
        p.setKind(kind);
        p.setName(name);
        p.setPartyType(Enums.PartyType.valueOf(type));
        p.setIdNumber(idNo);
        p.setPhone(phone);
        p.setEmail(null);
        p.setAddress("الإمارات العربية المتحدة");
        p.setActive(true);
        return partyRepo.save(p);
    }

    // ==================== الملفات المالية ====================

    private void seedFinancial() {
        User l1 = users.get("lawyer1");
        User l2 = users.get("lawyer2");

        // ١ — مفتوح حديثاً
        FinancialFile f1 = file(clients.get(0), debtors.get(0), l1, "185000", "مطالبة بقيمة توريدات غير مسددة",
                Enums.FileStatus.OPEN, today.minusDays(6));

        // ٢ — قيد التواصل
        FinancialFile f2 = file(clients.get(1), debtors.get(1), l1, "64000", "مستحقات عقد خدمات",
                Enums.FileStatus.CONTACTED, today.minusDays(21));
        comm(f2, Enums.CommType.CALL, "اتصال هاتفي بالمدين للاستفسار عن سبب التأخر",
                "وعد بالسداد خلال أسبوعين", today.minusDays(18));

        // ٣ — تم الإنذار
        FinancialFile f3 = file(clients.get(2), debtors.get(2), l2, "97500", "شيكات مرتجعة",
                Enums.FileStatus.WARNED, today.minusDays(40));
        comm(f3, Enums.CommType.CALL, "محاولة تواصل هاتفي", "لم يُرد على الاتصال", today.minusDays(35));
        comm(f3, Enums.CommType.WARNING, "إنذار رسمي بالسداد خلال ١٥ يوماً",
                "سُلّم الإنذار بالبريد المسجّل", today.minusDays(28));

        // ٤ — خطة تقسيط نشطة مع أقساط مسددة
        FinancialFile f4 = file(clients.get(3), debtors.get(3), l1, "120000", "تسوية بالتقسيط على ٦ أشهر",
                Enums.FileStatus.INSTALLMENT, today.minusDays(90));
        comm(f4, Enums.CommType.MEETING, "اجتماع تسوية مع المدين", "اتُّفق على التقسيط", today.minusDays(85));
        PaymentPlan plan = plan(f4, "120000", 6, today.minusMonths(3));
        List<Installment> insts = plan.getInstallments();
        payInstallment(f4, insts.get(0), "20000", today.minusMonths(3));
        payInstallment(f4, insts.get(1), "20000", today.minusMonths(2));
        recalc(f4);

        // ٥ — شيك مرتجع أعاد الأقساط للاستحقاق
        FinancialFile f5 = file(clients.get(4), debtors.get(4), l2, "80000", "تقسيط بشيكات — ارتجع أحدها",
                Enums.FileStatus.INSTALLMENT, today.minusDays(120));
        PaymentPlan plan5 = plan(f5, "80000", 4, today.minusMonths(4));
        List<Installment> in5 = plan5.getInstallments();
        payInstallment(f5, in5.get(0), "20000", today.minusMonths(4));
        Payment bounced = payment(f5, in5.get(1), "20000", today.minusMonths(3), Enums.PaymentMethod.CHEQUE);
        bounced.setStatus(Enums.PaymentStatus.BOUNCED);
        bounced.setBouncedAt(LocalDateTime.now().minusDays(50));
        bounced.setBounceReason("رصيد غير كافٍ — أُعيد القسط إلى الاستحقاق");
        paymentRepo.save(bounced);
        in5.get(1).setPaidAmount(BigDecimal.ZERO);
        in5.get(1).setStatus(Enums.InstallmentStatus.OVERDUE);
        instRepo.save(in5.get(1));
        recalc(f5);

        // ٦ — سداد جزئي
        FinancialFile f6 = file(clients.get(5), debtors.get(5), l1, "45000", "دفعة تحت الحساب",
                Enums.FileStatus.PARTIALLY_PAID, today.minusDays(55));
        payment(f6, null, "15000", today.minusDays(30), Enums.PaymentMethod.TRANSFER);
        recalc(f6);

        // ٧ — مسدد بالكامل ومؤرشف
        FinancialFile f7 = file(clients.get(6), debtors.get(6), l2, "36000", "مطالبة سُدّدت كاملة",
                Enums.FileStatus.PAID, today.minusDays(150));
        payment(f7, null, "36000", today.minusDays(100), Enums.PaymentMethod.TRANSFER);
        recalc(f7);
        closeFile(f7, Enums.ClosureType.FULL_PAYMENT, "سداد كامل بتحويل بنكي مع إيصال موثّق");

        // ٨ — مغلق برأي قانوني
        FinancialFile f8 = file(clients.get(7), debtors.get(7), l1, "22000", "مطالبة قديمة تعذّر الوصول لصاحبها",
                Enums.FileStatus.CLOSED_OPINION, today.minusDays(400));
        closeFile(f8, Enums.ClosureType.LEGAL_OPINION,
                "رأي قانوني بالإغلاق: المطالبة تعود لعام ٢٠٢٣ وتعذّر الوصول للمدين رغم ثلاث محاولات موثّقة");

        // ٩ — إعفاء معتمد من الإدارة
        FinancialFile f9 = file(clients.get(0), debtors.get(1), l2, "18000", "إعفاء لظروف اجتماعية موثّقة",
                Enums.FileStatus.EXEMPTED, today.minusDays(200));
        f9.setExemptedAmount(new BigDecimal("18000"));
        f9.setApprovedBy(users.get("admin"));
        fileRepo.save(f9);
        closeFile(f9, Enums.ClosureType.EXEMPTION, "إعفاء بموافقة المدير مع إرفاق مستندات الحالة");

        log.info("أُنشئت {} ملفات مالية تجريبية", fileRepo.count());
    }

    private FinancialFile file(Party client, Party debtor, User lawyer, String amount,
                               String subject, Enums.FileStatus status, LocalDate opened) {
        FinancialFile f = new FinancialFile();
        f.setFileNumber(String.format("FIN-%d-%04d", opened.getYear(), ++fin));
        f.setClient(client);
        f.setDebtor(debtor);
        f.setAssignedLawyer(lawyer);
        f.setClaimAmount(new BigDecimal(amount));
        f.setPaidAmount(BigDecimal.ZERO);
        f.setExemptedAmount(BigDecimal.ZERO);
        f.setSubject(subject);
        f.setDescription(subject + " — ملف تجريبي للعرض.");
        f.setStatus(status);
        f.setOpenedAt(opened);
        return fileRepo.save(f);
    }

    private void comm(FinancialFile f, Enums.CommType type, String summary, String outcome, LocalDate when) {
        Communication c = new Communication();
        c.setFinancialFileId(f.getId());
        c.setType(type);
        c.setCommDate(when.atTime(10, 30));
        c.setSummary(summary);
        c.setOutcome(outcome);
        c.setContactPerson(f.getDebtor().getName());
        if (type == Enums.CommType.WARNING) {
            c.setReferenceNo("WRN-" + when.getYear() + "-" + f.getId());
        }
        commRepo.save(c);
    }

    private PaymentPlan plan(FinancialFile f, String total, int count, LocalDate start) {
        PaymentPlan p = new PaymentPlan();
        p.setFinancialFileId(f.getId());
        p.setTotalAmount(new BigDecimal(total));
        p.setInstallmentsCount(count);
        p.setStartDate(start);
        p.setIntervalMonths(1);
        p.setPaymentMethod(Enums.PaymentMethod.CHEQUE);
        p.setNotes("تعهد موقّع من المدين بالسداد على " + count + " أقساط شهرية");
        p.setActive(true);
        PaymentPlan saved = planRepo.save(p);

        BigDecimal each = new BigDecimal(total).divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.DOWN);
        BigDecimal acc = BigDecimal.ZERO;
        List<Installment> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Installment inst = new Installment();
            inst.setPlan(saved);
            inst.setFinancialFileId(f.getId());
            inst.setSeq(i);
            inst.setDueDate(start.plusMonths(i - 1L));
            BigDecimal amt = (i == count) ? new BigDecimal(total).subtract(acc) : each;
            acc = acc.add(each);
            inst.setAmount(amt);
            inst.setPaidAmount(BigDecimal.ZERO);
            inst.setStatus(inst.getDueDate().isBefore(today)
                    ? Enums.InstallmentStatus.OVERDUE : Enums.InstallmentStatus.DUE);
            list.add(instRepo.save(inst));
        }
        saved.setInstallments(list);
        return saved;
    }

    private void payInstallment(FinancialFile f, Installment inst, String amount, LocalDate when) {
        payment(f, inst, amount, when, Enums.PaymentMethod.CHEQUE);
        inst.setPaidAmount(new BigDecimal(amount));
        inst.setStatus(Enums.InstallmentStatus.PAID);
        instRepo.save(inst);
    }

    private Payment payment(FinancialFile f, Installment inst, String amount,
                            LocalDate when, Enums.PaymentMethod method) {
        Payment p = new Payment();
        p.setFinancialFileId(f.getId());
        p.setInstallmentId(inst == null ? null : inst.getId());
        p.setReceiptNumber(String.format("RCP-%d-%04d", when.getYear(), ++rcp));
        p.setAmount(new BigDecimal(amount));
        p.setPaymentDate(when);
        p.setMethod(method);
        p.setStatus(Enums.PaymentStatus.CONFIRMED);
        p.setPayerName(f.getDebtor().getName());
        p.setReceivedBy(f.getAssignedLawyer());
        p.setConfirmedBy(users.get("admin"));
        p.setConfirmedAt(when.atTime(12, 0));
        if (method == Enums.PaymentMethod.CHEQUE) {
            p.setBankName("بنك أبوظبي الأول");
            p.setReferenceNo("CHQ-" + (100000 + rcp));
        }
        return paymentRepo.save(p);
    }

    /** يعيد حساب المحصّل من الدفعات المؤكدة فقط — كما تفعل قاعدة "المالي يقود الحالة". */
    private void recalc(FinancialFile f) {
        BigDecimal paid = paymentRepo.findByFinancialFileIdAndStatus(f.getId(), Enums.PaymentStatus.CONFIRMED)
                .stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        f.setPaidAmount(paid);
        fileRepo.save(f);
    }

    private void closeFile(FinancialFile f, Enums.ClosureType type, String note) {
        f.setClosureType(type);
        f.setClosureNote(note);
        f.setClosedAt(LocalDateTime.now().minusDays(5));
        f.setClosedBy(users.get("admin"));
        f.setArchived(true);
        fileRepo.save(f);
        archive(Enums.ArchiveType.FINANCIAL_FILE, "FINANCIAL_FILE", f.getId(), f.getFileNumber(),
                "ملف مالي " + f.getFileNumber() + " — " + f.getSubject(), note,
                f.getClient().getName(), null, f.getOpenedAt());
    }

    // ==================== القضايا ====================

    private void seedCases() {
        User l1 = users.get("lawyer1");
        User l2 = users.get("lawyer2");
        int appealDays = 30;

        // ١ — بلا حكم بعد (ممنوع التحويل)
        LegalCase c1 = legalCase(clients.get(0), debtors.get(0), l1, Enums.CaseType.COMMERCIAL,
                "مطالبة بقيمة توريدات", "185000", Enums.CaseStatus.IN_PROGRESS, today.minusDays(120));
        hearing(c1, today.minusDays(40), Enums.HearingType.FIRST, "حضر الطرفان وطُلبت مذكرات");
        hearing(c1, today.plusDays(9), Enums.HearingType.PLEADING, null);

        // ٢ — حكم لصالح الخصم (ممنوع)
        LegalCase c2 = legalCase(clients.get(1), debtors.get(1), l2, Enums.CaseType.LABOR,
                "دعوى عمالية", "64000", Enums.CaseStatus.JUDGED, today.minusDays(200));
        judgment(c2, today.minusDays(60), "2026/ح/1187", Enums.JudgmentFor.OPPONENT, "0",
                "قضت المحكمة برفض الدعوى", false, appealDays);

        // ٣ — اليوم هو آخر يوم في أجل الطعن (ممنوع حتى الغد)
        LegalCase c3 = legalCase(clients.get(2), debtors.get(2), l1, Enums.CaseType.CIVIL,
                "مطالبة بقيمة شيكات مرتجعة", "97500", Enums.CaseStatus.JUDGED, today.minusDays(150));
        judgment(c3, today.minusDays(appealDays), "2026/ح/1204", Enums.JudgmentFor.CLIENT, "97500",
                "قضت المحكمة بإلزام المدعى عليه بالمبلغ المطالب به", false, appealDays);
        hearing(c3, today.minusDays(appealDays), Enums.HearingType.JUDGMENT, "صدر الحكم");

        // ٤ — استئناف معلّق يمنع التحويل رغم انقضاء الأجل
        LegalCase c4 = legalCase(clients.get(3), debtors.get(3), l2, Enums.CaseType.COMMERCIAL,
                "نزاع عقد مقاولة", "310000", Enums.CaseStatus.APPEALED, today.minusDays(300));
        judgment(c4, today.minusDays(90), "2026/ح/1150", Enums.JudgmentFor.CLIENT, "310000",
                "قضت المحكمة لصالح المدعي", false, appealDays);
        Appeal a = new Appeal();
        a.setCaseId(c4.getId());
        a.setAppealNumber("2026/س/220");
        a.setFiledDate(today.minusDays(75));
        a.setFiledBy(Enums.AppealBy.OPPONENT);
        a.setStatus(Enums.AppealStatus.PENDING);
        a.setCourt("محكمة الاستئناف");
        a.setNotes("استئناف مقدّم من الخصم — التحويل للتنفيذ ممنوع حتى الفصل فيه");
        appealRepo.save(a);

        // ٥ — مستوفية كل الشروط الستة وجاهزة للتحويل
        LegalCase c5 = legalCase(clients.get(4), debtors.get(4), l1, Enums.CaseType.CIVIL,
                "مطالبة مالية — الحكم نهائي", "150000", Enums.CaseStatus.JUDGED, today.minusDays(260));
        judgment(c5, today.minusDays(80), "2026/ح/1099", Enums.JudgmentFor.CLIENT, "150000",
                "حكم نهائي بإلزام المدعى عليه بالسداد", true, appealDays);
        hearing(c5, today.minusDays(80), Enums.HearingType.JUDGMENT, "صدر الحكم نهائياً");

        // ٦ — جاهزة أيضاً بانقضاء الأجل دون استئناف
        LegalCase c6 = legalCase(clients.get(5), debtors.get(5), l2, Enums.CaseType.COMMERCIAL,
                "مطالبة بقيمة بضائع", "88000", Enums.CaseStatus.JUDGED, today.minusDays(240));
        judgment(c6, today.minusDays(70), "2026/ح/1112", Enums.JudgmentFor.PARTIAL, "70000",
                "حكم جزئي لصالح المدعي — انقضى أجل الطعن دون استئناف", false, appealDays);

        // ٧ و ٨ — سبق تحويلهما للتنفيذ (تُربط في seedExecutions)
        legalCase(clients.get(6), debtors.get(6), l1, Enums.CaseType.CIVIL,
                "مطالبة محوّلة للتنفيذ", "210000", Enums.CaseStatus.TRANSFERRED, today.minusDays(400));
        legalCase(clients.get(7), debtors.get(7), l2, Enums.CaseType.COMMERCIAL,
                "مطالبة مستوفاة", "95000", Enums.CaseStatus.TRANSFERRED, today.minusDays(500));

        log.info("أُنشئت {} قضايا تجريبية", caseRepo.count());
    }

    private LegalCase legalCase(Party client, Party opponent, User lawyer, Enums.CaseType type,
                                String subject, String amount, Enums.CaseStatus status, LocalDate opened) {
        LegalCase c = new LegalCase();
        c.setCaseNumber(String.format("CASE-%d-%04d", opened.getYear(), ++cas));
        c.setCourtCaseNumber(opened.getYear() + "/" + (2000 + cas));
        c.setCourt("محكمة أبوظبي الابتدائية");
        c.setCaseType(type);
        c.setClient(client);
        c.setOpponent(opponent);
        c.setAssignedLawyer(lawyer);
        c.setAssignmentReason("إسناد حسب التخصص والحمل الحالي");
        c.setSubject(subject);
        c.setDescription(subject + " — قضية تجريبية للعرض.");
        c.setClaimAmount(new BigDecimal(amount));
        c.setStatus(status);
        c.setOpenedAt(opened);
        c.setFiledAt(opened.plusDays(7));
        return caseRepo.save(c);
    }

    private void judgment(LegalCase c, LocalDate date, String number, Enums.JudgmentFor forWhom,
                          String amount, String summary, boolean isFinal, int appealDays) {
        c.setJudgmentDate(date);
        c.setJudgmentNumber(number);
        c.setJudgmentFor(forWhom);
        c.setJudgmentAmount(new BigDecimal(amount));
        c.setJudgmentSummary(summary);
        c.setJudgmentFinal(isFinal);
        c.setAppealDays(appealDays);
        c.setAppealDeadline(date.plusDays(appealDays));
        caseRepo.save(c);
    }

    private void hearing(LegalCase c, LocalDate date, Enums.HearingType type, String result) {
        Hearing h = new Hearing();
        h.setCaseId(c.getId());
        h.setHearingDate(date);
        h.setHearingTime(LocalTime.of(9, 30));
        h.setType(type);
        h.setCourt(c.getCourt());
        h.setRoom("قاعة " + (1 + (int) (c.getId() % 5)));
        h.setResult(result);
        h.setAttended(result != null);
        hearingRepo.save(h);
    }

    // ==================== التنفيذ ====================

    private void seedExecutions() {
        List<LegalCase> transferred = caseRepo.findByStatus(Enums.CaseStatus.TRANSFERRED);
        if (transferred.isEmpty()) {
            return;
        }

        // ملف تنفيذ بثلاثة أوامر سارية
        LegalCase c1 = transferred.get(0);
        ExecutionFile e1 = execution(c1, "210000", "8500", "60000", Enums.ExecStatus.IN_PROGRESS);
        c1.setExecutionFileId(e1.getId());
        caseRepo.save(c1);
        order(e1, Enums.OrderType.BANK_FREEZE, "مصرف الإمارات المركزي — كل البنوك العاملة بالدولة");
        order(e1, Enums.OrderType.TRAVEL_BAN, "الإدارة العامة للإقامة وشؤون الأجانب");
        order(e1, Enums.OrderType.SALARY_SEIZURE, "جهة عمل المنفَّذ ضده");
        // دفعات مؤكدة تسند المبلغ المحصّل
        execPayment(e1, "40000", e1.getOpenedAt().plusDays(20));
        execPayment(e1, "20000", e1.getOpenedAt().plusDays(45));

        // ملف تنفيذ مستوفى ومغلق ومؤرشف بشهادة
        if (transferred.size() > 1) {
            LegalCase c2 = transferred.get(1);
            ExecutionFile e2 = execution(c2, "95000", "4200", "99200", Enums.ExecStatus.CLOSED);
            c2.setExecutionFileId(e2.getId());
            c2.setStatus(Enums.CaseStatus.CLOSED);
            c2.setClosedAt(LocalDateTime.now().minusDays(20));
            caseRepo.save(c2);

            // استُوفي المبلغ كاملاً بدفعتين مؤكدتين — لذلك صار المتبقي صفراً
            execPayment(e2, "60000", e2.getOpenedAt().plusDays(15));
            execPayment(e2, "39200", e2.getOpenedAt().plusDays(40));

            ExecutionOrder done = order(e2, Enums.OrderType.BANK_FREEZE, "بنك أبوظبي التجاري");
            done.setStatus(Enums.OrderStatus.CANCELLED);
            done.setCancelledAt(LocalDateTime.now().minusDays(20));
            done.setCancelReason("اكتمال الاستيفاء");
            orderRepo.save(done);

            e2.setSatisfiedAt(LocalDateTime.now().minusDays(20));
            e2.setClosedAt(LocalDateTime.now().minusDays(20));
            e2.setCertificateNumber(String.format("CERT-%d-0001", today.getYear()));
            e2.setArchived(true);
            execRepo.save(e2);
            archive(Enums.ArchiveType.EXECUTION, "EXECUTION", e2.getId(), e2.getExecutionNumber(),
                    "ملف تنفيذ " + e2.getExecutionNumber() + " — مستوفى",
                    "اكتمل الاستيفاء وصدرت شهادة رقم " + e2.getCertificateNumber(),
                    e2.getClient().getName(), e2.getCourt(), e2.getOpenedAt());
        }
        log.info("أُنشئت {} ملفات تنفيذ تجريبية", execRepo.count());
    }

    private ExecutionFile execution(LegalCase c, String judgment, String expenses,
                                    String collected, Enums.ExecStatus status) {
        ExecutionFile e = new ExecutionFile();
        e.setExecutionNumber(String.format("EXE-%d-%04d", today.getYear(), ++exe));
        e.setCourtExecutionNumber(today.getYear() + "/تنفيذ/" + (500 + exe));
        e.setCaseId(c.getId());
        e.setClient(c.getClient());
        e.setDebtor(c.getOpponent());
        e.setAssignedLawyer(c.getAssignedLawyer());
        e.setCourt("محكمة التنفيذ — أبوظبي");
        e.setJudgmentAmount(new BigDecimal(judgment));
        e.setExpensesAmount(new BigDecimal(expenses));
        e.setCollectedAmount(new BigDecimal(collected));
        e.setStatus(status);
        e.setOpenedAt(c.getJudgmentDate() == null ? today.minusDays(60) : c.getJudgmentDate().plusDays(35));
        e.setWritRecievedAt(e.getOpenedAt());
        e.setNotes("ملف تنفيذ تجريبي للعرض.");
        return execRepo.save(e);
    }

    /**
     * دفعة مؤكدة على ملف تنفيذ.
     * القاعدة الذهبية «المالي يقود الحالة» تعني أن المحصّل يُشتق من الدفعات المؤكدة،
     * فلا يكفي ضبط الحقل مباشرة — لا بد من دفعات حقيقية تسنده.
     */
    private void execPayment(ExecutionFile e, String amount, LocalDate when) {
        Payment p = new Payment();
        p.setExecutionFileId(e.getId());
        p.setReceiptNumber(String.format("RCP-%d-%04d", when.getYear(), ++rcp));
        p.setAmount(new BigDecimal(amount));
        p.setPaymentDate(when);
        p.setMethod(Enums.PaymentMethod.TRANSFER);
        p.setStatus(Enums.PaymentStatus.CONFIRMED);
        p.setPayerName(e.getDebtor().getName());
        p.setReceivedBy(e.getAssignedLawyer());
        p.setConfirmedBy(users.get("admin"));
        p.setConfirmedAt(when.atTime(12, 0));
        paymentRepo.save(p);
    }

    private ExecutionOrder order(ExecutionFile e, Enums.OrderType type, String target) {
        ExecutionOrder o = new ExecutionOrder();
        o.setExecutionFileId(e.getId());
        o.setOrderType(type);
        o.setOrderNumber("ORD-" + today.getYear() + "-" + (1000 + (int) (Math.abs(e.getId()) * 10 + type.ordinal())));
        o.setIssuedDate(e.getOpenedAt().plusDays(5));
        o.setStatus(Enums.OrderStatus.ACTIVE);
        o.setTargetEntity(target);
        o.setDetails("أمر " + type.label() + " صادر عن محكمة التنفيذ");
        o.setIssuedBy(e.getAssignedLawyer());
        return orderRepo.save(o);
    }

    // ==================== الاستشارات ====================

    private void seedConsultations() {
        User k = users.get("consult1");
        User aa = users.get("consult2");
        User sr = users.get("senior1");

        consultation("مدى جواز إنهاء عقد عمل غير محدد المدة", "عمالي", aa,
                Enums.ConsultStatus.RECEIVED, null, clients.get(0), today.minusDays(2), today.plusDays(5));

        Consultation c2 = consultation("أثر شرط التحكيم في عقد التوريد", "تجاري", k,
                Enums.ConsultStatus.STUDYING, null, clients.get(1), today.minusDays(6), today.plusDays(3));
        action(c2, "ASSIGN", null, Enums.ConsultStatus.RECEIVED, k, "إسناد تلقائي حسب التخصص والحمل");
        action(c2, "START_STUDY", Enums.ConsultStatus.RECEIVED, Enums.ConsultStatus.STUDYING, k, null);

        Consultation c3 = consultation("التزامات المؤجر في عقد الإيجار التجاري", "تجاري", k,
                Enums.ConsultStatus.UNDER_REVIEW,
                "يلتزم المؤجر بتمكين المستأجر من الانتفاع بالعين المؤجرة طوال مدة العقد، "
                        + "ولا يجوز له إجراء أي تعديل يخل بهذا الانتفاع.",
                clients.get(2), today.minusDays(12), today.plusDays(1));
        action(c3, "SUBMIT_REVIEW", Enums.ConsultStatus.STUDYING, Enums.ConsultStatus.UNDER_REVIEW, k, null);

        Consultation c4 = consultation("حدود المسؤولية التقصيرية للشركة عن أفعال تابعيها", "تجاري", k,
                Enums.ConsultStatus.RETURNED,
                "مسودة أولى تحتاج إسناداً لنصوص المواد.",
                clients.get(3), today.minusDays(20), today.minusDays(2));
        c4.setReviewer(sr);
        c4.setReviewNotes("يرجى إسناد الرأي إلى مواد قانون المعاملات المدنية وذكر أرقامها صراحة");
        c4.setReviewedAt(LocalDateTime.now().minusDays(3));
        c4.setReturnedCount(1);
        consultRepo.save(c4);
        action(c4, "RETURN", Enums.ConsultStatus.UNDER_REVIEW, Enums.ConsultStatus.RETURNED, sr,
                c4.getReviewNotes());

        Consultation c5 = consultation("صحة الشرط الجزائي في عقود المقاولات", "تجاري", k,
                Enums.ConsultStatus.APPROVED,
                "الشرط الجزائي صحيح ما لم يكن مبالغاً فيه، وللقاضي تعديله بما يتناسب مع الضرر الفعلي.",
                clients.get(4), today.minusDays(25), today.minusDays(5));
        c5.setReviewer(sr);
        c5.setReviewedAt(LocalDateTime.now().minusDays(4));
        c5.setApprovedBy(sr);
        c5.setApprovedAt(LocalDateTime.now().minusDays(4));
        consultRepo.save(c5);
        action(c5, "APPROVE", Enums.ConsultStatus.UNDER_REVIEW, Enums.ConsultStatus.APPROVED, sr, "اعتُمد الرأي");

        // موقّعة ومقفلة
        Consultation c6 = consultation("أحقية العامل في مكافأة نهاية الخدمة عند الاستقالة", "عمالي", aa,
                Enums.ConsultStatus.SIGNED,
                "يستحق العامل مكافأة نهاية الخدمة عند الاستقالة وفق المدد المقررة قانوناً، "
                        + "وتُحتسب على أساس الأجر الأساسي الأخير.",
                clients.get(5), today.minusDays(45), today.minusDays(25));
        c6.setReviewer(sr);
        c6.setReviewedAt(LocalDateTime.now().minusDays(30));
        c6.setApprovedBy(sr);
        c6.setApprovedAt(LocalDateTime.now().minusDays(29));
        c6.setSignedBy(sr);
        c6.setSignedAt(LocalDateTime.now().minusDays(28));
        c6.setLocked(true);
        consultRepo.save(c6);
        action(c6, "SIGN", Enums.ConsultStatus.APPROVED, Enums.ConsultStatus.SIGNED, sr,
                "توقيع نهائي — الرأي مقفل");

        // رأي تصحيحي للرأي الموقّع
        Consultation c7 = consultation("تصحيح للرأي رقم " + c6.getConsultationNumber()
                        + ": احتساب مكافأة نهاية الخدمة", "عمالي", aa,
                Enums.ConsultStatus.STUDYING, null, clients.get(5), today.minusDays(10), today.plusDays(4));
        c7.setSupersedesId(c6.getId());
        consultRepo.save(c7);
        action(c7, "ASSIGN", null, Enums.ConsultStatus.RECEIVED, aa, "رأي تصحيحي للرأي الموقّع");

        // مرسلة ومؤرشفة
        Consultation c8 = consultation("نطاق شرط عدم المنافسة في عقود العمل", "عمالي", aa,
                Enums.ConsultStatus.SENT,
                "يشترط لصحة شرط عدم المنافسة تحديد الزمان والمكان ونوع العمل، وألا يزيد على سنتين.",
                clients.get(6), today.minusDays(70), today.minusDays(50));
        c8.setReviewer(sr);
        c8.setApprovedBy(sr);
        c8.setSignedBy(sr);
        c8.setSignedAt(LocalDateTime.now().minusDays(52));
        c8.setLocked(true);
        c8.setSentAt(LocalDateTime.now().minusDays(50));
        c8.setSentTo(clients.get(6).getName());
        consultRepo.save(c8);

        Consultation c9 = consultation("أثر القوة القاهرة على تنفيذ العقود", "تجاري", sr,
                Enums.ConsultStatus.ARCHIVED,
                "للقاضي عند تحقق الظروف الطارئة أن يرد الالتزام المرهق إلى الحد المعقول.",
                clients.get(7), today.minusDays(180), today.minusDays(160));
        c9.setSignedBy(sr);
        c9.setSignedAt(LocalDateTime.now().minusDays(165));
        c9.setLocked(true);
        c9.setArchived(true);
        consultRepo.save(c9);
        archive(Enums.ArchiveType.CONSULTATION, "CONSULTATION", c9.getId(), c9.getConsultationNumber(),
                "رأي قانوني: " + c9.getSubject(), c9.getOpinionText(),
                c9.getClient().getName(), null, c9.getReceivedAt());

        consultation("مدى جواز الحجز على الحساب المشترك", "تجاري", k,
                Enums.ConsultStatus.RECEIVED, null, clients.get(0), today.minusDays(1), today.plusDays(7));

        log.info("أُنشئت {} استشارة تجريبية", consultRepo.count());
    }

    private Consultation consultation(String subject, String spec, User consultant,
                                      Enums.ConsultStatus status, String opinion, Party client,
                                      LocalDate received, LocalDate due) {
        Consultation c = new Consultation();
        c.setConsultationNumber(String.format("CON-%d-%04d", received.getYear(), ++con));
        c.setClient(client);
        c.setSubject(subject);
        c.setSpecialization(spec);
        c.setRequestText("طلب رأي قانوني في: " + subject);
        c.setPriority(Enums.Priority.NORMAL);
        c.setStatus(status);
        c.setConsultant(consultant);
        c.setAssignmentReason("إسناد تلقائي: تخصص مطابق (50.0) + حمل منخفض (24.0) + أقدمية (12.0) = 86.0");
        c.setAssignmentScore(86.0);
        c.setAssignedManually(false);
        c.setReceivedAt(received);
        c.setDueDate(due);
        c.setOpinionText(opinion);
        if (opinion != null) {
            c.setOpinionWrittenAt(received.plusDays(2).atTime(11, 0));
        }
        return consultRepo.save(c);
    }

    private void action(Consultation c, String act, Enums.ConsultStatus from,
                        Enums.ConsultStatus to, User actor, String notes) {
        ConsultationAction a = new ConsultationAction();
        a.setConsultationId(c.getId());
        a.setAction(act);
        a.setFromStatus(from);
        a.setToStatus(to);
        a.setActor(actor);
        a.setActedAt(c.getReceivedAt().atTime(9, 0));
        a.setNotes(notes);
        actionRepo.save(a);
    }

    // ==================== المكتبة القانونية ====================

    private void seedLaws() {
        LawCode civil = lawCode("قانون المعاملات المدنية", "قانون اتحادي رقم (5) لسنة 1985", 1985);
        LawChapter b1 = chapter(civil, null, "الباب الأول", "مصادر الالتزام", 1);
        LawChapter f11 = chapter(civil, b1, "الفصل الأول", "العقد", 1);
        article(civil, f11, "125", "تعريف العقد",
                "العقد ارتباط الإيجاب الصادر من أحد العاقدين بقبول الآخر وتوافقهما على وجه يثبت أثره في المعقود عليه.",
                "عقد إيجاب قبول التزام", 1);
        article(civil, f11, "129", "أركان العقد",
                "يلزم لانعقاد العقد اتفاق طرفيه على العناصر الأساسية للالتزام، وأن يكون محل العقد قابلاً لحكمه، "
                        + "وأن يكون للعقد غرض مشروع.",
                "أركان محل سبب", 2);
        article(civil, f11, "246", "حسن النية في التنفيذ",
                "يجب تنفيذ العقد طبقاً لما اشتمل عليه وبطريقة تتفق مع ما يوجبه حسن النية.",
                "تنفيذ حسن النية", 3);
        LawChapter f12 = chapter(civil, b1, "الفصل الثاني", "الفعل الضار", 2);
        article(civil, f12, "282", "الضمان",
                "كل إضرار بالغير يلزم فاعله ولو غير مميز بضمان الضرر.",
                "ضرر ضمان مسؤولية تقصيرية", 1);
        article(civil, f12, "313", "مسؤولية المتبوع",
                "لا يكون أحد مسؤولاً عن فعل غيره، ومع ذلك فللمحكمة بناءً على طلب المضرور أن تلزم "
                        + "المتبوع بأداء ما حكم به على التابع من ضمان بسبب عمل غير مشروع وقع منه أثناء تأدية وظيفته.",
                "متبوع تابع مسؤولية", 2);
        LawChapter b2 = chapter(civil, null, "الباب الثاني", "آثار الالتزام", 2);
        LawChapter f21 = chapter(civil, b2, "الفصل الأول", "التنفيذ", 1);
        article(civil, f21, "390", "الشرط الجزائي",
                "يجوز للمتعاقدين أن يحددا مقدماً قيمة الضمان بالنص عليها في العقد أو في اتفاق لاحق. "
                        + "وللقاضي في جميع الأحوال بناءً على طلب أحد الطرفين أن يعدل في هذا الاتفاق بما يجعل "
                        + "التقدير مساوياً للضرر.",
                "شرط جزائي تعويض ضرر", 1);
        article(civil, f21, "249", "الظروف الطارئة",
                "إذا طرأت حوادث استثنائية عامة غير متوقعة وترتب على حدوثها أن تنفيذ الالتزام التعاقدي "
                        + "صار مرهقاً للمدين بحيث يهدده بخسارة فادحة، جاز للقاضي أن يرد الالتزام المرهق إلى الحد المعقول.",
                "ظروف طارئة قوة قاهرة", 2);

        LawCode labor = lawCode("قانون تنظيم علاقات العمل", "مرسوم بقانون اتحادي رقم (33) لسنة 2021", 2021);
        LawChapter lb1 = chapter(labor, null, "الباب الأول", "عقد العمل", 1);
        LawChapter lf11 = chapter(labor, lb1, "الفصل الأول", "إبرام العقد وأنواعه", 1);
        article(labor, lf11, "8", "مدة عقد العمل",
                "يبرم عقد العمل لمدة محددة لا تتجاوز ثلاث سنوات، ويجوز للطرفين تجديده أو تمديده لمدة "
                        + "مماثلة أو أقل مرة أو أكثر بالاتفاق.",
                "عقد عمل مدة تجديد", 1);
        article(labor, lf11, "9", "فترة التجربة",
                "لا يجوز أن تزيد فترة التجربة على ستة أشهر، ولا يجوز إخضاع العامل لأكثر من فترة تجربة "
                        + "واحدة لدى صاحب العمل نفسه.",
                "فترة تجربة", 2);
        article(labor, lf11, "10", "عدم المنافسة",
                "إذا كان العمل المنوط بالعامل يسمح له بمعرفة عملاء صاحب العمل أو أسرار عمله، جاز الاتفاق "
                        + "على ألا يقوم العامل بعد انتهاء العقد بمنافسته، على أن يكون هذا الشرط محدداً من حيث "
                        + "الزمان والمكان ونوع العمل وبما لا يتجاوز سنتين.",
                "عدم منافسة أسرار عملاء", 3);
        LawChapter lb2 = chapter(labor, null, "الباب الثاني", "انتهاء علاقة العمل", 2);
        LawChapter lf21 = chapter(labor, lb2, "الفصل الأول", "الإنهاء والمستحقات", 1);
        article(labor, lf21, "43", "الإخطار بالإنهاء",
                "يجوز لأي من طرفي العقد إنهاؤه لأي سبب مشروع بشرط إخطار الطرف الآخر كتابةً والاستمرار "
                        + "في تنفيذ العقد خلال مدة الإخطار المتفق عليها بما لا يقل عن ثلاثين يوماً ولا يزيد على تسعين يوماً.",
                "إنهاء إخطار مدة", 1);
        article(labor, lf21, "51", "مكافأة نهاية الخدمة",
                "يستحق العامل الأجنبي الذي أكمل سنة أو أكثر من الخدمة المستمرة مكافأة نهاية الخدمة عن مدة "
                        + "خدمته، تُحتسب على أساس أجر أساسي بواقع أجر واحد وعشرين يوماً عن كل سنة من السنوات "
                        + "الخمس الأولى، وأجر ثلاثين يوماً عن كل سنة بعد ذلك.",
                "مكافأة نهاية خدمة استقالة", 2);
        log.info("أُنشئ تشريعان بأبوابهما وفصولهما ومواد كاملة النص");
    }

    private LawCode lawCode(String title, String number, int year) {
        LawCode c = new LawCode();
        c.setTitle(title);
        c.setLawNumber(number);
        c.setIssueYear(year);
        c.setJurisdiction("الإمارات العربية المتحدة");
        c.setDescription(title + " — " + number);
        c.setActive(true);
        return lawCodeRepo.save(c);
    }

    private LawChapter chapter(LawCode code, LawChapter parent, String number, String title, int order) {
        LawChapter c = new LawChapter();
        c.setLawCodeId(code.getId());
        c.setParentId(parent == null ? null : parent.getId());
        c.setLevel(parent == null ? LawChapter.Level.BAB : LawChapter.Level.FASL);
        c.setChapterNumber(number);
        c.setTitle(title);
        c.setSortOrder(order);
        return chapterRepo.save(c);
    }

    private void article(LawCode code, LawChapter chapter, String number, String title,
                         String text, String keywords, int order) {
        LawArticle a = new LawArticle();
        a.setLawCodeId(code.getId());
        a.setChapterId(chapter.getId());
        a.setArticleNumber(number);
        a.setTitle(title);
        a.setArticleText(text);
        a.setKeywords(keywords);
        a.setSortOrder(order);
        articleRepo.save(a);
    }

    // ==================== الأرشيف ====================

    private void seedArchive() {
        arch(Enums.ArchiveType.JUDGMENT, "حكم بإلزام المدعى عليه بقيمة الشيكات المرتجعة",
                "قضت المحكمة بإلزام المدعى عليه بأداء مبلغ 97,500 درهم والفائدة القانونية والمصروفات.",
                "شيك مرتجع مطالبة حكم", "أحكام تجارية");
        arch(Enums.ArchiveType.JUDGMENT, "حكم برفض دعوى عمالية لانقضاء مدة التقادم",
                "قضت المحكمة برفض الدعوى لرفعها بعد مضي سنة من تاريخ انتهاء علاقة العمل.",
                "تقادم دعوى عمالية رفض", "أحكام عمالية");
        arch(Enums.ArchiveType.JUDGMENT, "حكم استئنافي بتعديل الشرط الجزائي",
                "عدّلت محكمة الاستئناف مبلغ الشرط الجزائي بما يتناسب مع الضرر الفعلي.",
                "شرط جزائي استئناف تعديل", "أحكام تجارية");
        arch(Enums.ArchiveType.OPINION, "رأي قانوني في صحة شرط التحكيم",
                "شرط التحكيم صحيح متى كان مكتوباً وصادراً ممن يملك أهلية التصرف.",
                "تحكيم شرط اختصاص", "آراء تجارية");
        arch(Enums.ArchiveType.OPINION, "رأي في احتساب مكافأة نهاية الخدمة",
                "تُحتسب المكافأة على أساس الأجر الأساسي الأخير دون البدلات.",
                "مكافأة نهاية خدمة أجر", "آراء عمالية");
        arch(Enums.ArchiveType.OPINION, "رأي في مسؤولية الشركة عن أفعال تابعيها",
                "تقوم مسؤولية المتبوع متى وقع الفعل الضار أثناء تأدية الوظيفة أو بسببها.",
                "متبوع تابع مسؤولية", "آراء مدنية");
        arch(Enums.ArchiveType.TEMPLATE, "نموذج إنذار عدلي بالسداد",
                "نموذج جاهز لإنذار المدين بالسداد خلال خمسة عشر يوماً من تاريخ الإعلان.",
                "إنذار سداد نموذج", "نماذج");
        arch(Enums.ArchiveType.TEMPLATE, "نموذج اتفاقية تسوية بالتقسيط",
                "نموذج اتفاقية تسوية ودية بجدول أقساط وتعهد بالسداد.",
                "تسوية تقسيط تعهد نموذج", "نماذج");
        arch(Enums.ArchiveType.TEMPLATE, "نموذج طلب أمر تنفيذ بحجز بنكي",
                "نموذج طلب يُقدَّم لمحكمة التنفيذ لإصدار أمر بالحجز على الحسابات البنكية.",
                "حجز بنكي تنفيذ نموذج", "نماذج");
        arch(Enums.ArchiveType.TEMPLATE, "نموذج مذكرة دفاع",
                "هيكل مذكرة دفاع يتضمن الوقائع والدفوع الشكلية والموضوعية والطلبات.",
                "مذكرة دفاع دفوع", "نماذج");
        arch(Enums.ArchiveType.REFERENCE, "دليل إجراءات التنفيذ أمام محاكم أبوظبي",
                "مرجع إجرائي يوضح خطوات فتح ملف التنفيذ وأنواع الأوامر ومواعيدها.",
                "تنفيذ إجراءات دليل", "مراجع");
        arch(Enums.ArchiveType.REFERENCE, "جدول مواعيد الطعن في الأحكام",
                "ملخص للمواعيد القانونية للطعن بالاستئناف والتمييز وأثر انقضائها.",
                "طعن مواعيد استئناف تمييز", "مراجع");
        arch(Enums.ArchiveType.COMMENTARY, "تعليق على تطبيق الظروف الطارئة",
                "تحليل لشروط إعمال نظرية الظروف الطارئة وحدود سلطة القاضي.",
                "ظروف طارئة تعليق", "تعليقات");
        arch(Enums.ArchiveType.COMMENTARY, "تعليق على شرط عدم المنافسة",
                "ملاحظات على اشتراط تحديد الزمان والمكان ونوع العمل لصحة الشرط.",
                "عدم منافسة تعليق", "تعليقات");
        arch(Enums.ArchiveType.REFERENCE, "دليل تحصيل المديونيات ودياً",
                "خطوات التحصيل الودي من فتح الملف حتى التصعيد أو التسوية.",
                "تحصيل ودي مديونية", "مراجع");
        log.info("أُنشئ {} عنصر أرشيف", archiveRepo.count());
    }

    private void arch(Enums.ArchiveType type, String title, String content, String keywords, String category) {
        archive(type, null, null, null, title, content, null, null, today.minusDays(20L + arc));
        ArchiveItem last = archiveRepo.findAll().stream()
                .max(Comparator.comparing(ArchiveItem::getId)).orElse(null);
        if (last != null) {
            last.setKeywords(keywords);
            last.setCategory(category);
            archiveRepo.save(last);
        }
    }

    private void archive(Enums.ArchiveType type, String sourceType, Long sourceId, String sourceNumber,
                         String title, String content, String clientName, String court, LocalDate date) {
        ArchiveItem a = new ArchiveItem();
        a.setReferenceNo(String.format("ARC-%d-%04d", today.getYear(), ++arc));
        a.setItemType(type);
        a.setTitle(title);
        a.setSummary(content == null ? null : (content.length() > 400 ? content.substring(0, 400) : content));
        a.setContent(content);
        a.setCategory(type.label());
        a.setItemDate(date);
        a.setSourceType(sourceType);
        a.setSourceId(sourceId);
        a.setSourceNumber(sourceNumber);
        a.setClientName(clientName);
        a.setCourtName(court);
        a.setKeywords(title);
        archiveRepo.save(a);
    }

    // ==================== العدّادات ====================

    /** يضبط العدّادات لتكمل من بعد الأرقام التجريبية. */
    private void seedSequences() {
        seq("FIN", "FIN", fin);
        seq("CASE", "CASE", cas);
        seq("EXE", "EXE", exe);
        seq("CON", "CON", con);
        seq("RCP", "RCP", rcp);
        seq("ARC", "ARC", arc);
        seq("CERT", "CERT", 1);
    }

    private void seq(String key, String prefix, long last) {
        NumberSequence s = new NumberSequence();
        s.setSeqKey(key);
        s.setSeqYear(today.getYear());
        s.setPrefix(prefix);
        s.setLastValue(last);
        seqRepo.save(s);
    }
}
