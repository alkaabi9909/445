/* =====================================================================
   نظام قانون — مخطط قاعدة البيانات لـ Microsoft SQL Server
   ---------------------------------------------------------------------
   • كل الأعمدة النصية NVARCHAR لأن محتوى النظام عربي بالكامل.
   • أعمدة البحث والعناوين بترتيب مقارنة Arabic_CI_AS.
   • السكربت قابل لإعادة التشغيل بأمان (يتحقق من وجود كل كائن قبل إنشائه).
   • شغّله على قاعدة QanoonERP المنشأة بـ sql/00_create_database.sql
   ===================================================================== */

-- ===================== الجداول =====================

-- الاستئنافات
IF OBJECT_ID(N'dbo.appeals', N'U') IS NULL
CREATE TABLE appeals (
    decision_date date,
    filed_date date not null,
    case_id bigint not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    updated_at datetime2(6),
    filed_by nvarchar(20) not null check (filed_by in ('CLIENT','OPPONENT')),
    status nvarchar(20) not null check (status in ('PENDING','DECIDED','WITHDRAWN')),
    appeal_number nvarchar(60),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    court nvarchar(150),
    notes nvarchar(1000) COLLATE Arabic_CI_AS,
    decision_summary nvarchar(2000),
    primary key (id)
);
GO

-- الأرشيف الشامل
IF OBJECT_ID(N'dbo.archive_items', N'U') IS NULL
CREATE TABLE archive_items (
    confidential bit not null,
    item_date date,
    created_at datetime2(6) not null,
    id bigint identity not null,
    law_code_id bigint,
    source_id bigint,
    updated_at datetime2(6),
    item_type nvarchar(30) not null check (item_type in ('JUDGMENT','OPINION','TEMPLATE','REFERENCE','COMMENTARY','FINANCIAL_FILE','CASE','EXECUTION','CONSULTATION')),
    source_type nvarchar(30),
    reference_no nvarchar(40) not null,
    source_number nvarchar(40),
    category nvarchar(100),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    court_name nvarchar(150) COLLATE Arabic_CI_AS,
    client_name nvarchar(200) COLLATE Arabic_CI_AS,
    title nvarchar(400) COLLATE Arabic_CI_AS not null,
    keywords nvarchar(500) COLLATE Arabic_CI_AS,
    summary nvarchar(500) COLLATE Arabic_CI_AS,
    content nvarchar(4000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- المرفقات
IF OBJECT_ID(N'dbo.attachments', N'U') IS NULL
CREATE TABLE attachments (
    copied_from_id bigint,
    created_at datetime2(6) not null,
    entity_id bigint not null,
    file_size bigint,
    id bigint identity not null,
    updated_at datetime2(6),
    entity_type nvarchar(40) not null,
    category nvarchar(60),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    content_type nvarchar(120),
    description nvarchar(300) COLLATE Arabic_CI_AS,
    file_name nvarchar(255) not null,
    stored_name nvarchar(255) not null,
    primary key (id)
);
GO

-- سجل النشاطات
IF OBJECT_ID(N'dbo.audit_logs', N'U') IS NULL
CREATE TABLE audit_logs (
    success bit not null,
    acted_at datetime2(6) not null,
    entity_id bigint,
    id bigint identity not null,
    action nvarchar(30) not null,
    entity_type nvarchar(40),
    entity_ref nvarchar(60),
    ip_address nvarchar(60),
    username nvarchar(60) not null,
    full_name nvarchar(150) COLLATE Arabic_CI_AS,
    user_agent nvarchar(300),
    details nvarchar(2000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- سجل التواصل والإنذارات
IF OBJECT_ID(N'dbo.communications', N'U') IS NULL
CREATE TABLE communications (
    case_id bigint,
    comm_date datetime2(6) not null,
    copied_from_id bigint,
    created_at datetime2(6) not null,
    financial_file_id bigint,
    id bigint identity not null,
    updated_at datetime2(6),
    type nvarchar(20) not null check (type in ('CALL','SMS','EMAIL','WARNING','MEETING','LETTER')),
    reference_no nvarchar(60),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    contact_person nvarchar(150),
    outcome nvarchar(500),
    summary nvarchar(1000) COLLATE Arabic_CI_AS not null,
    primary key (id)
);
GO

-- سجل مراحل الاستشارة
IF OBJECT_ID(N'dbo.consultation_actions', N'U') IS NULL
CREATE TABLE consultation_actions (
    acted_at datetime2(6) not null,
    actor_id bigint,
    consultation_id bigint not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    updated_at datetime2(6),
    from_status nvarchar(20) check (from_status in ('RECEIVED','STUDYING','UNDER_REVIEW','RETURNED','APPROVED','SIGNED','SENT','ARCHIVED')),
    to_status nvarchar(20) check (to_status in ('RECEIVED','STUDYING','UNDER_REVIEW','RETURNED','APPROVED','SIGNED','SENT','ARCHIVED')),
    action nvarchar(30) not null,
    created_by nvarchar(100),
    updated_by nvarchar(100),
    notes nvarchar(2000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- المسار الثالث: الاستشارات القانونية
IF OBJECT_ID(N'dbo.consultations', N'U') IS NULL
CREATE TABLE consultations (
    archived bit not null,
    assigned_manually bit not null,
    assignment_score float(53),
    due_date date,
    locked bit not null,
    received_at date not null,
    returned_count int not null,
    approved_at datetime2(6),
    approved_by_id bigint,
    client_id bigint,
    consultant_id bigint,
    created_at datetime2(6) not null,
    id bigint identity not null,
    opinion_written_at datetime2(6),
    reviewed_at datetime2(6),
    reviewer_id bigint,
    sent_at datetime2(6),
    signed_at datetime2(6),
    signed_by_id bigint,
    supersedes_id bigint,
    updated_at datetime2(6),
    priority nvarchar(20) not null check (priority in ('LOW','NORMAL','HIGH','URGENT')),
    status nvarchar(20) not null check (status in ('RECEIVED','STUDYING','UNDER_REVIEW','RETURNED','APPROVED','SIGNED','SENT','ARCHIVED')),
    consultation_number nvarchar(30) not null,
    created_by nvarchar(100),
    specialization nvarchar(100),
    updated_by nvarchar(100),
    sent_to nvarchar(200),
    subject nvarchar(300) COLLATE Arabic_CI_AS not null,
    assignment_reason nvarchar(600),
    review_notes nvarchar(2000),
    opinion_text nvarchar(4000) COLLATE Arabic_CI_AS,
    request_text nvarchar(4000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- ملفات التنفيذ
IF OBJECT_ID(N'dbo.execution_files', N'U') IS NULL
CREATE TABLE execution_files (
    archived bit not null,
    collected_amount numeric(18,2) not null,
    expenses_amount numeric(18,2) not null,
    judgment_amount numeric(18,2) not null,
    opened_at date not null,
    writ_received_at date,
    assigned_lawyer_id bigint,
    case_id bigint not null,
    client_id bigint not null,
    closed_at datetime2(6),
    created_at datetime2(6) not null,
    debtor_id bigint not null,
    id bigint identity not null,
    satisfied_at datetime2(6),
    updated_at datetime2(6),
    status nvarchar(20) not null check (status in ('OPEN','IN_PROGRESS','SATISFIED','CLOSED')),
    execution_number nvarchar(30) not null,
    certificate_number nvarchar(40),
    court_execution_number nvarchar(60),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    court nvarchar(150),
    notes nvarchar(2000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- أوامر التنفيذ الخمسة
IF OBJECT_ID(N'dbo.execution_orders', N'U') IS NULL
CREATE TABLE execution_orders (
    issued_date date not null,
    cancelled_at datetime2(6),
    created_at datetime2(6) not null,
    execution_file_id bigint not null,
    id bigint identity not null,
    issued_by_id bigint,
    updated_at datetime2(6),
    status nvarchar(20) not null check (status in ('ACTIVE','EXECUTED','CANCELLED')),
    order_type nvarchar(30) not null check (order_type in ('BANK_FREEZE','TRAVEL_BAN','ARREST','PROPERTY_SEIZURE','SALARY_SEIZURE')),
    order_number nvarchar(60),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    target_entity nvarchar(200),
    cancel_reason nvarchar(500),
    details nvarchar(2000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- المسار الأول: الملفات المالية
IF OBJECT_ID(N'dbo.financial_files', N'U') IS NULL
CREATE TABLE financial_files (
    archived bit not null,
    claim_amount numeric(18,2) not null,
    exempted_amount numeric(18,2) not null,
    opened_at date not null,
    paid_amount numeric(18,2) not null,
    approved_by_id bigint,
    assigned_lawyer_id bigint,
    client_id bigint not null,
    closed_at datetime2(6),
    closed_by_id bigint,
    created_at datetime2(6) not null,
    debtor_id bigint not null,
    escalated_case_id bigint,
    id bigint identity not null,
    updated_at datetime2(6),
    closure_type nvarchar(30) check (closure_type in ('FULL_PAYMENT','INSTALLMENT','LEGAL_OPINION','EXEMPTION','ESCALATION')),
    file_number nvarchar(30) not null,
    status nvarchar(30) not null check (status in ('OPEN','CONTACTED','WARNED','INSTALLMENT','PARTIALLY_PAID','PAID','CLOSED_OPINION','EXEMPTED','ESCALATED','ARCHIVED')),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    subject nvarchar(300) COLLATE Arabic_CI_AS,
    closure_note nvarchar(2000),
    description nvarchar(2000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- الجلسات
IF OBJECT_ID(N'dbo.hearings', N'U') IS NULL
CREATE TABLE hearings (
    attended bit not null,
    hearing_date date not null,
    hearing_time time,
    next_hearing_date date,
    case_id bigint not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    updated_at datetime2(6),
    type nvarchar(20) not null check (type in ('FIRST','PLEADING','EVIDENCE','EXPERT','JUDGMENT','OTHER')),
    room nvarchar(60),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    court nvarchar(150),
    notes nvarchar(2000) COLLATE Arabic_CI_AS,
    result nvarchar(2000),
    primary key (id)
);
GO

-- الأقساط
IF OBJECT_ID(N'dbo.installments', N'U') IS NULL
CREATE TABLE installments (
    amount numeric(18,2) not null,
    due_date date not null,
    paid_amount numeric(18,2) not null,
    seq int not null,
    created_at datetime2(6) not null,
    financial_file_id bigint not null,
    id bigint identity not null,
    plan_id bigint not null,
    updated_at datetime2(6),
    status nvarchar(20) not null check (status in ('DUE','PARTIAL','PAID','OVERDUE','CANCELLED')),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    cancel_reason nvarchar(500),
    primary key (id)
);
GO

-- المواد القانونية
IF OBJECT_ID(N'dbo.law_articles', N'U') IS NULL
CREATE TABLE law_articles (
    sort_order int not null,
    chapter_id bigint,
    created_at datetime2(6) not null,
    id bigint identity not null,
    law_code_id bigint not null,
    updated_at datetime2(6),
    article_number nvarchar(40) not null,
    created_by nvarchar(100),
    updated_by nvarchar(100),
    title nvarchar(300) COLLATE Arabic_CI_AS,
    keywords nvarchar(500) COLLATE Arabic_CI_AS,
    article_text nvarchar(4000) COLLATE Arabic_CI_AS not null,
    primary key (id)
);
GO

-- الأبواب والفصول
IF OBJECT_ID(N'dbo.law_chapters', N'U') IS NULL
CREATE TABLE law_chapters (
    sort_order int not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    law_code_id bigint not null,
    parent_id bigint,
    updated_at datetime2(6),
    level nvarchar(10) not null check (level in ('BAB','FASL')),
    chapter_number nvarchar(40),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    title nvarchar(300) COLLATE Arabic_CI_AS not null,
    primary key (id)
);
GO

-- التشريعات
IF OBJECT_ID(N'dbo.law_codes', N'U') IS NULL
CREATE TABLE law_codes (
    active bit not null,
    issue_year int,
    created_at datetime2(6) not null,
    id bigint identity not null,
    updated_at datetime2(6),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    law_number nvarchar(120),
    jurisdiction nvarchar(150),
    title nvarchar(300) COLLATE Arabic_CI_AS not null,
    description nvarchar(2000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- المسار الثاني: القضايا
IF OBJECT_ID(N'dbo.legal_cases', N'U') IS NULL
CREATE TABLE legal_cases (
    appeal_days int,
    appeal_deadline date,
    archived bit not null,
    claim_amount numeric(18,2),
    filed_at date,
    judgment_amount numeric(18,2),
    judgment_date date,
    judgment_final bit not null,
    opened_at date not null,
    assigned_lawyer_id bigint,
    client_id bigint not null,
    closed_at datetime2(6),
    created_at datetime2(6) not null,
    execution_file_id bigint,
    id bigint identity not null,
    opponent_id bigint not null,
    source_financial_file_id bigint,
    updated_at datetime2(6),
    judgment_for nvarchar(20) check (judgment_for in ('CLIENT','OPPONENT','PARTIAL')),
    case_number nvarchar(30) not null,
    case_type nvarchar(30) not null check (case_type in ('CIVIL','COMMERCIAL','LABOR','CRIMINAL','FAMILY','REAL_ESTATE','ADMINISTRATIVE')),
    status nvarchar(30) not null check (status in ('OPEN','IN_PROGRESS','JUDGED','APPEALED','TRANSFERRED','CLOSED')),
    court_case_number nvarchar(60),
    judgment_number nvarchar(60),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    court nvarchar(150),
    subject nvarchar(300) COLLATE Arabic_CI_AS not null,
    assignment_reason nvarchar(500),
    closure_note nvarchar(1000),
    description nvarchar(3000) COLLATE Arabic_CI_AS,
    judgment_summary nvarchar(3000),
    primary key (id)
);
GO

-- التنبيهات
IF OBJECT_ID(N'dbo.notifications', N'U') IS NULL
CREATE TABLE notifications (
    due_date date,
    is_read bit not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    link_id bigint,
    user_id bigint not null,
    type nvarchar(20) not null check (type in ('INFO','WARNING','DEADLINE','TASK')),
    link_type nvarchar(30),
    dedupe_key nvarchar(120),
    title nvarchar(200) COLLATE Arabic_CI_AS not null,
    message nvarchar(1000),
    primary key (id)
);
GO

-- عدّادات أرقام المستندات
IF OBJECT_ID(N'dbo.number_sequences', N'U') IS NULL
CREATE TABLE number_sequences (
    seq_year int not null,
    id bigint identity not null,
    last_value bigint not null,
    prefix nvarchar(10) not null,
    seq_key nvarchar(30) not null,
    primary key (id)
);
GO

-- الأطراف: الموكلون والمدينون
IF OBJECT_ID(N'dbo.parties', N'U') IS NULL
CREATE TABLE parties (
    active bit not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    updated_at datetime2(6),
    kind nvarchar(20) not null check (kind in ('CLIENT','DEBTOR')),
    party_type nvarchar(20) not null check (party_type in ('INDIVIDUAL','COMPANY','GOVERNMENT')),
    phone nvarchar(30),
    id_number nvarchar(50),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    email nvarchar(150),
    name nvarchar(200) COLLATE Arabic_CI_AS not null,
    address nvarchar(300),
    notes nvarchar(500) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- خطط التقسيط
IF OBJECT_ID(N'dbo.payment_plans', N'U') IS NULL
CREATE TABLE payment_plans (
    active bit not null,
    installments_count int not null,
    interval_months int not null,
    start_date date not null,
    total_amount numeric(18,2) not null,
    created_at datetime2(6) not null,
    financial_file_id bigint not null,
    id bigint identity not null,
    updated_at datetime2(6),
    payment_method nvarchar(20) check (payment_method in ('CASH','CHEQUE','CARD','TRANSFER')),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    notes nvarchar(1000) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- الدفعات
IF OBJECT_ID(N'dbo.payments', N'U') IS NULL
CREATE TABLE payments (
    amount numeric(18,2) not null,
    payment_date date not null,
    bounced_at datetime2(6),
    confirmed_at datetime2(6),
    confirmed_by_id bigint,
    created_at datetime2(6) not null,
    execution_file_id bigint,
    financial_file_id bigint,
    id bigint identity not null,
    installment_id bigint,
    received_by_id bigint,
    updated_at datetime2(6),
    method nvarchar(20) not null check (method in ('CASH','CHEQUE','CARD','TRANSFER')),
    status nvarchar(20) not null check (status in ('PENDING','CONFIRMED','BOUNCED','CANCELLED')),
    receipt_number nvarchar(30) not null,
    reference_no nvarchar(80),
    created_by nvarchar(100),
    updated_by nvarchar(100),
    bank_name nvarchar(120),
    payer_name nvarchar(150),
    bounce_reason nvarchar(300),
    notes nvarchar(500) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- صلاحيات كل دور
IF OBJECT_ID(N'dbo.role_permissions', N'U') IS NULL
CREATE TABLE role_permissions (
    role_id bigint not null,
    permission nvarchar(50)
);
GO

-- الأدوار الوظيفية
IF OBJECT_ID(N'dbo.roles', N'U') IS NULL
CREATE TABLE roles (
    is_system bit not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    updated_at datetime2(6),
    code nvarchar(50) not null,
    created_by nvarchar(100),
    name_ar nvarchar(100) COLLATE Arabic_CI_AS not null,
    updated_by nvarchar(100),
    description nvarchar(300) COLLATE Arabic_CI_AS,
    primary key (id)
);
GO

-- البحوث المحفوظة
IF OBJECT_ID(N'dbo.saved_searches', N'U') IS NULL
CREATE TABLE saved_searches (
    is_saved bit not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    user_id bigint not null,
    name nvarchar(200) COLLATE Arabic_CI_AS,
    query_text nvarchar(500),
    filters_json nvarchar(2000),
    primary key (id)
);
GO

-- الإعدادات العامة
IF OBJECT_ID(N'dbo.system_settings', N'U') IS NULL
CREATE TABLE system_settings (
    created_at datetime2(6) not null,
    id bigint identity not null,
    updated_at datetime2(6),
    value_type nvarchar(20) not null,
    setting_group nvarchar(60),
    setting_key nvarchar(80) not null,
    created_by nvarchar(100),
    updated_by nvarchar(100),
    name_ar nvarchar(200) COLLATE Arabic_CI_AS not null,
    description nvarchar(500) COLLATE Arabic_CI_AS,
    setting_value nvarchar(500),
    primary key (id)
);
GO

-- المستخدمون
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
CREATE TABLE users (
    active bit not null,
    failed_attempts int not null,
    joined_at date,
    must_change_password bit not null,
    created_at datetime2(6) not null,
    id bigint identity not null,
    last_login_at datetime2(6),
    locked_until datetime2(6),
    role_id bigint not null,
    updated_at datetime2(6),
    phone nvarchar(30),
    username nvarchar(60) not null,
    created_by nvarchar(100),
    password_hash nvarchar(100) not null,
    specialization nvarchar(100),
    updated_by nvarchar(100),
    email nvarchar(150),
    full_name nvarchar(150) COLLATE Arabic_CI_AS not null,
    primary key (id)
);
GO


-- ===================== القيود الفريدة =====================

IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_archive_items_reference_no')
ALTER TABLE archive_items ADD CONSTRAINT UQ_archive_items_reference_no UNIQUE (reference_no);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_consultations_consultation_number')
ALTER TABLE consultations ADD CONSTRAINT UQ_consultations_consultation_number UNIQUE (consultation_number);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_execution_files_execution_number')
ALTER TABLE execution_files ADD CONSTRAINT UQ_execution_files_execution_number UNIQUE (execution_number);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_financial_files_file_number')
ALTER TABLE financial_files ADD CONSTRAINT UQ_financial_files_file_number UNIQUE (file_number);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_legal_cases_case_number')
ALTER TABLE legal_cases ADD CONSTRAINT UQ_legal_cases_case_number UNIQUE (case_number);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_number_sequences_seq_key__seq_year')
ALTER TABLE number_sequences ADD CONSTRAINT UQ_number_sequences_seq_key__seq_year UNIQUE (seq_key, seq_year);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_payments_receipt_number')
ALTER TABLE payments ADD CONSTRAINT UQ_payments_receipt_number UNIQUE (receipt_number);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_roles_code')
ALTER TABLE roles ADD CONSTRAINT UQ_roles_code UNIQUE (code);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_system_settings_setting_key')
ALTER TABLE system_settings ADD CONSTRAINT UQ_system_settings_setting_key UNIQUE (setting_key);
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_users_username')
ALTER TABLE users ADD CONSTRAINT UQ_users_username UNIQUE (username);
GO

-- ===================== الفهارس =====================

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_arch_type' AND object_id = OBJECT_ID(N'dbo.archive_items'))
CREATE INDEX ix_arch_type ON archive_items (item_type);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_arch_source' AND object_id = OBJECT_ID(N'dbo.archive_items'))
CREATE INDEX ix_arch_source ON archive_items (source_type, source_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_att_entity' AND object_id = OBJECT_ID(N'dbo.attachments'))
CREATE INDEX ix_att_entity ON attachments (entity_type, entity_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_audit_time' AND object_id = OBJECT_ID(N'dbo.audit_logs'))
CREATE INDEX ix_audit_time ON audit_logs (acted_at);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_audit_entity' AND object_id = OBJECT_ID(N'dbo.audit_logs'))
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_audit_user' AND object_id = OBJECT_ID(N'dbo.audit_logs'))
CREATE INDEX ix_audit_user ON audit_logs (username);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_article_chapter' AND object_id = OBJECT_ID(N'dbo.law_articles'))
CREATE INDEX ix_article_chapter ON law_articles (chapter_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_notif_user' AND object_id = OBJECT_ID(N'dbo.notifications'))
CREATE INDEX ix_notif_user ON notifications (user_id, is_read);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_fin_status' AND object_id = OBJECT_ID(N'dbo.financial_files'))
CREATE INDEX ix_fin_status ON financial_files (status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_fin_lawyer' AND object_id = OBJECT_ID(N'dbo.financial_files'))
CREATE INDEX ix_fin_lawyer ON financial_files (assigned_lawyer_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_case_status' AND object_id = OBJECT_ID(N'dbo.legal_cases'))
CREATE INDEX ix_case_status ON legal_cases (status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_case_lawyer' AND object_id = OBJECT_ID(N'dbo.legal_cases'))
CREATE INDEX ix_case_lawyer ON legal_cases (assigned_lawyer_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_case_deadline' AND object_id = OBJECT_ID(N'dbo.legal_cases'))
CREATE INDEX ix_case_deadline ON legal_cases (appeal_deadline);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_exec_status' AND object_id = OBJECT_ID(N'dbo.execution_files'))
CREATE INDEX ix_exec_status ON execution_files (status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_exec_case' AND object_id = OBJECT_ID(N'dbo.execution_files'))
CREATE INDEX ix_exec_case ON execution_files (case_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_cons_status' AND object_id = OBJECT_ID(N'dbo.consultations'))
CREATE INDEX ix_cons_status ON consultations (status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_cons_consultant' AND object_id = OBJECT_ID(N'dbo.consultations'))
CREATE INDEX ix_cons_consultant ON consultations (consultant_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_hearing_date' AND object_id = OBJECT_ID(N'dbo.hearings'))
CREATE INDEX ix_hearing_date ON hearings (hearing_date);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_inst_due' AND object_id = OBJECT_ID(N'dbo.installments'))
CREATE INDEX ix_inst_due ON installments (due_date);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_pay_file' AND object_id = OBJECT_ID(N'dbo.payments'))
CREATE INDEX ix_pay_file ON payments (financial_file_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_pay_exec' AND object_id = OBJECT_ID(N'dbo.payments'))
CREATE INDEX ix_pay_exec ON payments (execution_file_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_comm_file' AND object_id = OBJECT_ID(N'dbo.communications'))
CREATE INDEX ix_comm_file ON communications (financial_file_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_consact_cons' AND object_id = OBJECT_ID(N'dbo.consultation_actions'))
CREATE INDEX ix_consact_cons ON consultation_actions (consultation_id);
GO

-- ===================== المفاتيح الأجنبية =====================

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_consultation_actions_actor_id')
ALTER TABLE consultation_actions ADD CONSTRAINT FK_consultation_actions_actor_id FOREIGN KEY (actor_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_consultations_approved_by_id')
ALTER TABLE consultations ADD CONSTRAINT FK_consultations_approved_by_id FOREIGN KEY (approved_by_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_consultations_client_id')
ALTER TABLE consultations ADD CONSTRAINT FK_consultations_client_id FOREIGN KEY (client_id) REFERENCES parties (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_consultations_consultant_id')
ALTER TABLE consultations ADD CONSTRAINT FK_consultations_consultant_id FOREIGN KEY (consultant_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_consultations_reviewer_id')
ALTER TABLE consultations ADD CONSTRAINT FK_consultations_reviewer_id FOREIGN KEY (reviewer_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_consultations_signed_by_id')
ALTER TABLE consultations ADD CONSTRAINT FK_consultations_signed_by_id FOREIGN KEY (signed_by_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_execution_files_assigned_lawyer_id')
ALTER TABLE execution_files ADD CONSTRAINT FK_execution_files_assigned_lawyer_id FOREIGN KEY (assigned_lawyer_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_execution_files_client_id')
ALTER TABLE execution_files ADD CONSTRAINT FK_execution_files_client_id FOREIGN KEY (client_id) REFERENCES parties (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_execution_files_debtor_id')
ALTER TABLE execution_files ADD CONSTRAINT FK_execution_files_debtor_id FOREIGN KEY (debtor_id) REFERENCES parties (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_execution_orders_issued_by_id')
ALTER TABLE execution_orders ADD CONSTRAINT FK_execution_orders_issued_by_id FOREIGN KEY (issued_by_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_financial_files_approved_by_id')
ALTER TABLE financial_files ADD CONSTRAINT FK_financial_files_approved_by_id FOREIGN KEY (approved_by_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_financial_files_assigned_lawyer_id')
ALTER TABLE financial_files ADD CONSTRAINT FK_financial_files_assigned_lawyer_id FOREIGN KEY (assigned_lawyer_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_financial_files_client_id')
ALTER TABLE financial_files ADD CONSTRAINT FK_financial_files_client_id FOREIGN KEY (client_id) REFERENCES parties (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_financial_files_closed_by_id')
ALTER TABLE financial_files ADD CONSTRAINT FK_financial_files_closed_by_id FOREIGN KEY (closed_by_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_financial_files_debtor_id')
ALTER TABLE financial_files ADD CONSTRAINT FK_financial_files_debtor_id FOREIGN KEY (debtor_id) REFERENCES parties (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_installments_plan_id')
ALTER TABLE installments ADD CONSTRAINT FK_installments_plan_id FOREIGN KEY (plan_id) REFERENCES payment_plans (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_legal_cases_assigned_lawyer_id')
ALTER TABLE legal_cases ADD CONSTRAINT FK_legal_cases_assigned_lawyer_id FOREIGN KEY (assigned_lawyer_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_legal_cases_client_id')
ALTER TABLE legal_cases ADD CONSTRAINT FK_legal_cases_client_id FOREIGN KEY (client_id) REFERENCES parties (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_legal_cases_opponent_id')
ALTER TABLE legal_cases ADD CONSTRAINT FK_legal_cases_opponent_id FOREIGN KEY (opponent_id) REFERENCES parties (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_payments_confirmed_by_id')
ALTER TABLE payments ADD CONSTRAINT FK_payments_confirmed_by_id FOREIGN KEY (confirmed_by_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_payments_received_by_id')
ALTER TABLE payments ADD CONSTRAINT FK_payments_received_by_id FOREIGN KEY (received_by_id) REFERENCES users (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_role_permissions_role_id')
ALTER TABLE role_permissions ADD CONSTRAINT FK_role_permissions_role_id FOREIGN KEY (role_id) REFERENCES roles (id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_users_role_id')
ALTER TABLE users ADD CONSTRAINT FK_users_role_id FOREIGN KEY (role_id) REFERENCES roles (id);
GO
