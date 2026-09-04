/* =====================================================================
   نظام قانون — البيانات المرجعية
   ---------------------------------------------------------------------
   الأدوار الأربعة الأساسية + دور مخصص نموذجي، والإعدادات العامة،
   وقيم بدء عدّادات أرقام المستندات.
   لا تحتوي على بيانات تجريبية — تلك يزرعها التطبيق عند أول تشغيل.
   ===================================================================== */

-- ===================== الأدوار =====================

IF NOT EXISTS (SELECT 1 FROM roles WHERE code = N'MANAGER')
INSERT INTO roles (code, name_ar, description, is_system, created_at)
VALUES (N'MANAGER', N'المدير', N'رؤية كاملة على كل الملفات والتقارير والإعدادات', 1, SYSDATETIME());
GO

IF NOT EXISTS (SELECT 1 FROM roles WHERE code = N'LAWYER')
INSERT INTO roles (code, name_ar, description, is_system, created_at)
VALUES (N'LAWYER', N'المحامي', N'يرى القضايا والملفات المسندة إليه فقط', 1, SYSDATETIME());
GO

IF NOT EXISTS (SELECT 1 FROM roles WHERE code = N'CONSULTANT')
INSERT INTO roles (code, name_ar, description, is_system, created_at)
VALUES (N'CONSULTANT', N'المستشار القانوني', N'يرى استشاراته فقط ولا يصل لشاشات الإعدادات', 1, SYSDATETIME());
GO

IF NOT EXISTS (SELECT 1 FROM roles WHERE code = N'SENIOR_CONSULTANT')
INSERT INTO roles (code, name_ar, description, is_system, created_at)
VALUES (N'SENIOR_CONSULTANT', N'كبير المستشارين', N'صلاحية المراجعة والتوقيع النهائي على الآراء', 1, SYSDATETIME());
GO

IF NOT EXISTS (SELECT 1 FROM roles WHERE code = N'SECRETARY')
INSERT INTO roles (code, name_ar, description, is_system, created_at)
VALUES (N'SECRETARY', N'سكرتير / موظف إدخال بيانات', N'مثال على الأدوار المخصصة بصلاحيات دقيقة', 0, SYSDATETIME());
GO

-- ===================== صلاحيات الأدوار =====================

-- المدير: كل الصلاحيات
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    (N'DASHBOARD_VIEW'), (N'DASHBOARD_ALL'), (N'CLIENTS_VIEW'), (N'CLIENTS_MANAGE'),
    (N'FINANCIAL_VIEW'), (N'FINANCIAL_MANAGE'), (N'FINANCIAL_VIEW_ALL'), (N'PAYMENT_CONFIRM'),
    (N'EXEMPTION_APPROVE'), (N'CASE_VIEW'), (N'CASE_MANAGE'), (N'CASE_VIEW_ALL'),
    (N'CASE_ASSIGN'), (N'CASE_TRANSFER'), (N'EXECUTION_VIEW'), (N'EXECUTION_MANAGE'),
    (N'EXECUTION_VIEW_ALL'), (N'CONSULT_VIEW'), (N'CONSULT_MANAGE'), (N'CONSULT_VIEW_ALL'),
    (N'CONSULT_ASSIGN'), (N'CONSULT_REVIEW'), (N'CONSULT_SIGN'), (N'ARCHIVE_VIEW'),
    (N'ARCHIVE_MANAGE'), (N'REPORTS_VIEW'), (N'USERS_MANAGE'), (N'SETTINGS_MANAGE'),
    (N'AUDIT_VIEW'), (N'BACKUP_RUN')
) AS p(permission)
WHERE r.code = N'MANAGER'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);
GO

-- المحامي
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    (N'DASHBOARD_VIEW'), (N'CLIENTS_VIEW'), (N'CLIENTS_MANAGE'),
    (N'FINANCIAL_VIEW'), (N'FINANCIAL_MANAGE'), (N'PAYMENT_CONFIRM'),
    (N'CASE_VIEW'), (N'CASE_MANAGE'), (N'CASE_TRANSFER'),
    (N'EXECUTION_VIEW'), (N'EXECUTION_MANAGE'), (N'ARCHIVE_VIEW')
) AS p(permission)
WHERE r.code = N'LAWYER'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);
GO

-- المستشار القانوني
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    (N'DASHBOARD_VIEW'), (N'CONSULT_VIEW'), (N'CONSULT_MANAGE'),
    (N'ARCHIVE_VIEW'), (N'CLIENTS_VIEW')
) AS p(permission)
WHERE r.code = N'CONSULTANT'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);
GO

-- كبير المستشارين
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    (N'DASHBOARD_VIEW'), (N'CONSULT_VIEW'), (N'CONSULT_MANAGE'), (N'CONSULT_VIEW_ALL'),
    (N'CONSULT_ASSIGN'), (N'CONSULT_REVIEW'), (N'CONSULT_SIGN'),
    (N'ARCHIVE_VIEW'), (N'ARCHIVE_MANAGE'), (N'REPORTS_VIEW'), (N'CLIENTS_VIEW')
) AS p(permission)
WHERE r.code = N'SENIOR_CONSULTANT'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);
GO

-- سكرتير / موظف إدخال بيانات
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    (N'DASHBOARD_VIEW'), (N'CLIENTS_VIEW'), (N'CLIENTS_MANAGE'),
    (N'FINANCIAL_VIEW'), (N'FINANCIAL_VIEW_ALL'),
    (N'CASE_VIEW'), (N'CASE_VIEW_ALL'), (N'ARCHIVE_VIEW')
) AS p(permission)
WHERE r.code = N'SECRETARY'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission = p.permission);
GO

-- ===================== الإعدادات العامة =====================

INSERT INTO system_settings (setting_key, setting_value, name_ar, value_type, description, setting_group, created_at)
SELECT s.k, s.v, s.n, s.t, s.d, s.g, SYSDATETIME()
FROM (VALUES
    (N'office.name', N'مكتب المحاماة والاستشارات القانونية', N'اسم المكتب', N'TEXT',
     N'يظهر في الترويسة وفي المستندات المطبوعة', N'عام'),
    (N'currency', N'AED', N'العملة', N'TEXT',
     N'عملة المكتب — الدرهم الإماراتي', N'عام'),
    (N'appeal.days', N'30', N'مدة الطعن القانونية (يوم)', N'NUMBER',
     N'تُضاف لتاريخ الحكم لحساب أجل الطعن، وتتحكم في بوابة التحويل للتنفيذ', N'القضايا'),
    (N'appeal.alert.days', N'7', N'التنبيه قبل انتهاء أجل الطعن (يوم)', N'NUMBER',
     N'يبدأ التنبيه قبل هذه المدة ويستمر حتى اليوم الأخير من المهلة', N'القضايا'),
    (N'hearing.alert.days', N'3', N'التنبيه بالجلسات القادمة (يوم)', N'NUMBER',
     N'عدد الأيام التي تُعرض فيها الجلسة ضمن الملفات العاجلة', N'القضايا'),
    (N'installment.alert.days', N'3', N'التنبيه بالأقساط المستحقة (يوم)', N'NUMBER',
     N'عدد الأيام قبل استحقاق القسط لبدء التنبيه', N'المالية'),
    (N'password.min.length', N'8', N'أقل طول لكلمة المرور', N'NUMBER',
     N'مع اشتراط حرف كبير وحرف صغير ورقم ورمز', N'الأمان'),
    (N'max.failed.logins', N'5', N'عدد محاولات الدخول الفاشلة قبل القفل', N'NUMBER',
     N'يُقفل الحساب مؤقتاً بعد تجاوز هذا العدد', N'الأمان'),
    (N'lock.minutes', N'15', N'مدة قفل الحساب (دقيقة)', N'NUMBER',
     N'المدة التي يبقى فيها الحساب مقفلاً بعد المحاولات الفاشلة', N'الأمان'),
    (N'session.timeout.minutes', N'30', N'انتهاء الجلسة بعد عدم النشاط (دقيقة)', N'NUMBER',
     N'تُنهى الجلسة تلقائياً بعد هذه المدة من عدم النشاط', N'الأمان')
) AS s(k, v, n, t, d, g)
WHERE NOT EXISTS (SELECT 1 FROM system_settings ss WHERE ss.setting_key = s.k);
GO

-- ===================== عدّادات أرقام المستندات =====================
-- FIN ملف مالي | CASE قضية | EXE تنفيذ | CON استشارة | RCP إيصال | CERT شهادة استيفاء | ARC أرشيف

INSERT INTO number_sequences (seq_key, seq_year, last_value, prefix)
SELECT s.k, YEAR(GETDATE()), 0, s.k
FROM (VALUES (N'FIN'), (N'CASE'), (N'EXE'), (N'CON'), (N'RCP'), (N'CERT'), (N'ARC')) AS s(k)
WHERE NOT EXISTS (
    SELECT 1 FROM number_sequences ns
    WHERE ns.seq_key = s.k AND ns.seq_year = YEAR(GETDATE()));
GO
