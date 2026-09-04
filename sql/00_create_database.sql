/* =====================================================================
   نظام قانون — إنشاء قاعدة البيانات على Microsoft SQL Server
   ---------------------------------------------------------------------
   كيف تشغّل هذا السكربت:

   • من SQL Server Management Studio (SSMS):
       افتح الملف ثم اضغط Execute، وتأكد أنك متصل بحساب يملك صلاحية sysadmin.

   • من سطر الأوامر عبر sqlcmd (لاحظ ‎-f 65001‎ لدعم العربية):
       sqlcmd -S .\SQLEXPRESS -E -i "sql\00_create_database.sql" -f 65001

   ترتيب المقارنة Arabic_CI_AS يجعل البحث العربي غير حساس لحالة الأحرف.
   ===================================================================== */

-- ===================== إنشاء القاعدة =====================

IF DB_ID(N'QanoonERP') IS NULL
BEGIN
    CREATE DATABASE QanoonERP COLLATE Arabic_CI_AS;
    PRINT N'تم إنشاء قاعدة البيانات QanoonERP';
END
ELSE
    PRINT N'قاعدة البيانات QanoonERP موجودة مسبقاً';
GO

ALTER DATABASE QanoonERP SET RECOVERY SIMPLE;
GO

USE QanoonERP;
GO

/* =====================================================================
   حساب التطبيق — اختياري
   ---------------------------------------------------------------------
   التطبيق مهيّأ افتراضياً على «مصادقة ويندوز المدمجة»، فلا يحتاج
   اسم مستخدم ولا كلمة مرور ولا أي تغيير في إعدادات أمان الخادم.

   إن أردت بدلاً من ذلك استخدام حساب SQL مستقل، فعليك:
     ١) تفعيل الوضع المختلط (Mixed Mode Authentication) في خصائص الخادم
        من SSMS: Server Properties ← Security ← SQL Server and Windows
        Authentication mode، ثم إعادة تشغيل خدمة SQL Server.
     ٢) إزالة التعليق عن الكتلة أدناه بعد وضع كلمة مرور قوية.
     ٣) ضبط متغيّرات البيئة قبل تشغيل التطبيق:
          set QANOON_DB_URL=jdbc:sqlserver://localhost\SQLEXPRESS;databaseName=QanoonERP;encrypt=true;trustServerCertificate=true
          set QANOON_DB_USER=qanoon_app
          set QANOON_DB_PASSWORD=<كلمة المرور التي وضعتها>

   ملاحظة أمنية: لا تضع كلمة المرور الحقيقية داخل هذا الملف إن كان
   سيُحفظ في مستودع كود أو يُشارك مع أحد.
   ===================================================================== */

/*
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'qanoon_app')
BEGIN
    CREATE LOGIN qanoon_app
        WITH PASSWORD = N'<ضع كلمة مرور قوية هنا>',
             DEFAULT_DATABASE = QanoonERP,
             CHECK_POLICY = ON;
    PRINT N'تم إنشاء حساب الدخول qanoon_app';
END
GO

USE QanoonERP;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'qanoon_app')
BEGIN
    CREATE USER qanoon_app FOR LOGIN qanoon_app;
    ALTER ROLE db_owner ADD MEMBER qanoon_app;
    PRINT N'تم منح الحساب qanoon_app صلاحية db_owner على القاعدة';
END
GO
*/

/* =====================================================================
   منح صلاحية لمستخدم ويندوز الحالي (الوضع الافتراضي)
   ---------------------------------------------------------------------
   عادةً يكون المستخدم الذي ثبّت SQL Server Express عضواً في sysadmin
   تلقائياً، فلا حاجة لأي إجراء. أما إن أردت منح مستخدم ويندوز آخر
   صلاحية على القاعدة، فاستبدل الاسم ثم أزل التعليق:
   ===================================================================== */

/*
CREATE LOGIN [DOMAIN\username] FROM WINDOWS;
GO
USE QanoonERP;
GO
CREATE USER [DOMAIN\username] FOR LOGIN [DOMAIN\username];
ALTER ROLE db_owner ADD MEMBER [DOMAIN\username];
GO
*/

PRINT N'انتهى الإعداد. الخطوة التالية: شغّل التطبيق بـ mvn spring-boot:run -Dspring-boot.run.profiles=mssql';
PRINT N'سيتولى Flyway إنشاء الجداول والبيانات المرجعية تلقائياً عند أول تشغيل.';
GO
