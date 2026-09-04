/* =====================================================================
   نظام قانون — استعلامات جاهزة لإدارة المكتب
   ---------------------------------------------------------------------
   شغّلها على قاعدة QanoonERP. كل استعلام مستقل ويمكن تشغيله وحده.
   ===================================================================== */

USE QanoonERP;
GO

-- =====================================================================
-- ١) الملفات المالية المتأخرة: مضى عليها أكثر من ٣٠ يوماً ولم تُسدَّد
-- =====================================================================
SELECT
    f.file_number                                   AS [رقم الملف],
    c.name                                          AS [الموكل],
    d.name                                          AS [المدين],
    f.claim_amount                                  AS [المطالبة],
    f.paid_amount                                   AS [المحصّل],
    f.claim_amount - f.paid_amount - f.exempted_amount AS [المتبقي],
    f.status                                        AS [الحالة],
    u.full_name                                     AS [المحامي],
    DATEDIFF(DAY, f.opened_at, GETDATE())           AS [عمر الملف بالأيام]
FROM financial_files f
JOIN parties c ON c.id = f.client_id
JOIN parties d ON d.id = f.debtor_id
LEFT JOIN users u ON u.id = f.assigned_lawyer_id
WHERE f.closed_at IS NULL
  AND f.claim_amount - f.paid_amount - f.exempted_amount > 0
  AND DATEDIFF(DAY, f.opened_at, GETDATE()) > 30
ORDER BY [عمر الملف بالأيام] DESC;
GO

-- =====================================================================
-- ٢) القضايا التي أوشك أجل الطعن فيها على الانتهاء (خلال ٧ أيام)
-- =====================================================================
SELECT
    lc.case_number                              AS [رقم القضية],
    lc.court_case_number                        AS [رقم المحكمة],
    p.name                                      AS [الموكل],
    lc.judgment_number                          AS [رقم الحكم],
    lc.judgment_date                            AS [تاريخ الحكم],
    lc.appeal_deadline                          AS [ينتهي أجل الطعن],
    DATEDIFF(DAY, GETDATE(), lc.appeal_deadline) AS [الأيام المتبقية],
    u.full_name                                 AS [المحامي]
FROM legal_cases lc
JOIN parties p ON p.id = lc.client_id
LEFT JOIN users u ON u.id = lc.assigned_lawyer_id
WHERE lc.appeal_deadline IS NOT NULL
  AND lc.execution_file_id IS NULL
  AND lc.appeal_deadline BETWEEN CAST(GETDATE() AS DATE) AND DATEADD(DAY, 7, CAST(GETDATE() AS DATE))
ORDER BY lc.appeal_deadline;
GO

-- =====================================================================
-- ٣) الأقساط المستحقة خلال هذا الشهر
-- =====================================================================
SELECT
    f.file_number           AS [رقم الملف],
    d.name                  AS [المدين],
    i.seq                   AS [رقم القسط],
    i.due_date              AS [تاريخ الاستحقاق],
    i.amount                AS [المبلغ],
    i.paid_amount           AS [المسدد],
    i.amount - i.paid_amount AS [المتبقي من القسط],
    i.status                AS [حالة القسط]
FROM installments i
JOIN financial_files f ON f.id = i.financial_file_id
JOIN parties d ON d.id = f.debtor_id
WHERE i.status IN (N'DUE', N'PARTIAL', N'OVERDUE')
  AND i.due_date >= DATEFROMPARTS(YEAR(GETDATE()), MONTH(GETDATE()), 1)
  AND i.due_date < DATEADD(MONTH, 1, DATEFROMPARTS(YEAR(GETDATE()), MONTH(GETDATE()), 1))
ORDER BY i.due_date;
GO

-- =====================================================================
-- ٤) حمل كل مستشار — الاستشارات النشطة (غير الموقّعة/المرسلة/المؤرشفة)
-- =====================================================================
SELECT
    u.full_name                                            AS [المستشار],
    u.specialization                                       AS [التخصص],
    DATEDIFF(YEAR, u.joined_at, GETDATE())                 AS [سنوات الخدمة],
    COUNT(c.id)                                            AS [إجمالي الاستشارات],
    SUM(CASE WHEN c.status NOT IN (N'SIGNED', N'SENT', N'ARCHIVED')
             THEN 1 ELSE 0 END)                            AS [الحمل النشط],
    SUM(CASE WHEN c.signed_at IS NOT NULL THEN 1 ELSE 0 END) AS [الموقّعة],
    AVG(CASE WHEN c.signed_at IS NOT NULL
             THEN DATEDIFF(DAY, c.received_at, c.signed_at) END) AS [متوسط المدة بالأيام]
FROM users u
LEFT JOIN consultations c ON c.consultant_id = u.id
WHERE u.active = 1
GROUP BY u.full_name, u.specialization, u.joined_at
HAVING COUNT(c.id) > 0
ORDER BY [الحمل النشط] DESC;
GO

-- =====================================================================
-- ٥) نسبة التحصيل الشهرية خلال آخر اثني عشر شهراً
-- =====================================================================
SELECT
    FORMAT(p.payment_date, 'yyyy-MM')  AS [الشهر],
    COUNT(*)                           AS [عدد الدفعات],
    SUM(p.amount)                      AS [إجمالي المحصّل]
FROM payments p
WHERE p.status = N'CONFIRMED'
  AND p.payment_date >= DATEADD(MONTH, -12, CAST(GETDATE() AS DATE))
GROUP BY FORMAT(p.payment_date, 'yyyy-MM')
ORDER BY [الشهر];
GO

-- =====================================================================
-- ٦) أوامر التنفيذ السارية حالياً
-- =====================================================================
SELECT
    e.execution_number  AS [رقم التنفيذ],
    d.name              AS [المنفَّذ ضده],
    o.order_type        AS [نوع الأمر],
    o.order_number      AS [رقم الأمر],
    o.issued_date       AS [تاريخ الإصدار],
    o.target_entity     AS [الجهة المخاطَبة],
    e.judgment_amount + e.expenses_amount - e.collected_amount AS [المتبقي على الملف]
FROM execution_orders o
JOIN execution_files e ON e.id = o.execution_file_id
JOIN parties d ON d.id = e.debtor_id
WHERE o.status = N'ACTIVE'
ORDER BY o.issued_date DESC;
GO

-- =====================================================================
-- ٧) القضايا الجاهزة للتحويل للتنفيذ — الشروط الستة كاملة في WHERE واحد
--    ملاحظة: هذا الاستعلام للاطلاع فقط؛ التحويل الفعلي يمرّ عبر
--    بوابة التطبيق التي تعيد فحص الشروط على الخادم.
-- =====================================================================
SELECT
    lc.case_number      AS [رقم القضية],
    p.name              AS [الموكل],
    o.name              AS [الخصم],
    lc.judgment_number  AS [رقم الحكم],
    lc.judgment_date    AS [تاريخ الحكم],
    lc.judgment_amount  AS [المبلغ المحكوم به],
    lc.appeal_deadline  AS [انتهى أجل الطعن في],
    u.full_name         AS [المحامي]
FROM legal_cases lc
JOIN parties p ON p.id = lc.client_id
JOIN parties o ON o.id = lc.opponent_id
LEFT JOIN users u ON u.id = lc.assigned_lawyer_id
WHERE
    -- (١) حكم موثّق: تاريخه ورقمه مسجّلان
    lc.judgment_date IS NOT NULL
    AND lc.judgment_number IS NOT NULL
    -- (٢) الحكم ليس لصالح الخصم
    AND lc.judgment_for IN (N'CLIENT', N'PARTIAL')
    -- (٣) نهائية الحكم: موسوم نهائياً، أو انقضى أجل الطعن (اليوم الأخير لا يزال ضمن المهلة)
    AND (lc.judgment_final = 1
         OR (lc.appeal_deadline IS NOT NULL AND CAST(GETDATE() AS DATE) > lc.appeal_deadline))
    -- (٤) لا يوجد استئناف مُفعّل
    AND NOT EXISTS (SELECT 1 FROM appeals a
                    WHERE a.case_id = lc.id AND a.status = N'PENDING')
    -- (٥) لم يسبق التحويل
    AND lc.execution_file_id IS NULL
    AND NOT EXISTS (SELECT 1 FROM execution_files e WHERE e.case_id = lc.id)
    -- (٦) القضية غير مغلقة
    AND lc.status <> N'CLOSED'
ORDER BY lc.judgment_date DESC;
GO

-- =====================================================================
-- ٨) ملفات التنفيذ المستوفاة والجاهزة للإغلاق (المتبقي = صفر)
-- =====================================================================
SELECT
    e.execution_number  AS [رقم التنفيذ],
    c.name              AS [صاحب الحق],
    d.name              AS [المنفَّذ ضده],
    e.judgment_amount   AS [أصل الحكم],
    e.expenses_amount   AS [المصروفات],
    e.collected_amount  AS [المحصّل],
    (SELECT COUNT(*) FROM execution_orders o
     WHERE o.execution_file_id = e.id AND o.status = N'ACTIVE') AS [أوامر سارية]
FROM execution_files e
JOIN parties c ON c.id = e.client_id
JOIN parties d ON d.id = e.debtor_id
WHERE e.status <> N'CLOSED'
  AND e.judgment_amount + e.expenses_amount - e.collected_amount <= 0
ORDER BY e.execution_number;
GO

-- =====================================================================
-- ٩) محاولات الدخول الفاشلة خلال آخر سبعة أيام
-- =====================================================================
SELECT
    a.acted_at    AS [الوقت],
    a.username    AS [المستخدم],
    a.ip_address  AS [عنوان الجهاز],
    a.details     AS [التفاصيل]
FROM audit_logs a
WHERE a.success = 0
  AND a.entity_type = N'AUTH'
  AND a.acted_at >= DATEADD(DAY, -7, GETDATE())
ORDER BY a.acted_at DESC;
GO

-- =====================================================================
-- ١٠) ملخص عام لحجم عمل المكتب
-- =====================================================================
SELECT N'ملفات مالية مفتوحة' AS [البند],
       COUNT(*) AS [العدد] FROM financial_files WHERE archived = 0
UNION ALL
SELECT N'قضايا جارية', COUNT(*) FROM legal_cases WHERE status <> N'CLOSED' AND archived = 0
UNION ALL
SELECT N'ملفات تنفيذ مفتوحة', COUNT(*) FROM execution_files WHERE status <> N'CLOSED'
UNION ALL
SELECT N'استشارات جارية', COUNT(*) FROM consultations
       WHERE status NOT IN (N'SIGNED', N'SENT', N'ARCHIVED')
UNION ALL
SELECT N'عناصر الأرشيف', COUNT(*) FROM archive_items;
GO
