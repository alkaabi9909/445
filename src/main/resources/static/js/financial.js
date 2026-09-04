/* =====================================================================
   المسار الأول: الملفات المالية.
   الحالة تُشتق آلياً من الدفعات المؤكدة — والإغلاق عبر خمس وجهات موثّقة.
   ===================================================================== */

(function () {
  const { el, esc, fmt } = App;

  const statusTone = (s) => ({
    PAID: 'ok', CLOSED_OPINION: 'muted', ARCHIVED: 'muted', EXEMPTED: 'info',
    INSTALLMENT: 'warn', PARTIALLY_PAID: 'warn', WARNED: 'warn',
    ESCALATED: 'danger', OPEN: 'info', CONTACTED: 'info'
  }[s] || 'muted');

  App.registerPage('financial', {
    title: 'الملفات المالية',
    icon: 'work',
    permission: 'FINANCIAL_VIEW',
    render: async (root, params) => {
      if (params.id) await renderDetail(root, params.id);
      else await renderList(root);
    }
  });

  // ===================== القائمة =====================

  async function renderList(root) {
    const state = { status: '', q: '', page: 0, size: 20 };

    const head = el('div', 'page-head');
    head.innerHTML = `<div><h2>الملفات المالية</h2>
      <p class="page-sub">نقطة الدخول للتحصيل الودي قبل أي تصعيد قضائي</p></div>`;
    if (App.can('FINANCIAL_MANAGE')) {
      const btn = el('button', 'btn btn-gold', 'ملف مالي جديد');
      btn.onclick = () => openCreate();
      head.appendChild(btn);
    }
    root.appendChild(head);

    const filters = el('div', 'filters');
    const fq = el('label', 'fld grow');
    fq.innerHTML = '<span>بحث</span><input type="text" placeholder="رقم الملف، الموكل، المدين، الموضوع">';
    const fs = el('label', 'fld');
    fs.innerHTML = '<span>الحالة</span>';
    const sel = el('select');
    sel.appendChild(new Option('كل الحالات', ''));
    App.options('fileStatus').forEach(o => sel.appendChild(new Option(o.label, o.value)));
    fs.appendChild(sel);
    filters.appendChild(fq);
    filters.appendChild(fs);
    root.appendChild(filters);

    const listBox = el('div');
    root.appendChild(listBox);

    let timer = null;
    fq.querySelector('input').addEventListener('input', (e) => {
      clearTimeout(timer);
      timer = setTimeout(() => { state.q = e.target.value.trim(); state.page = 0; load(); }, 320);
    });
    sel.onchange = () => { state.status = sel.value; state.page = 0; load(); };

    async function load() {
      const stop = App.spinner(listBox);
      try {
        const res = await App.api.get('/api/financial', state);
        listBox.innerHTML = '';
        listBox.appendChild(App.table({
          columns: [
            { key: 'fileNumber', label: 'رقم الملف' },
            { key: 'clientName', label: 'الموكل' },
            { key: 'debtorName', label: 'المدين' },
            { key: 'claimAmount', label: 'المطالبة', align: 'num', format: v => fmt.money(v) },
            { key: 'paidAmount', label: 'المحصّل', align: 'num', format: v => fmt.money(v) },
            { key: 'remainingAmount', label: 'المتبقي', align: 'num', format: v => fmt.money(v) },
            { key: 'statusLabel', label: 'الحالة', align: 'center',
              format: (v, r) => App.badge(v, statusTone(r.status)) },
            { key: 'assignedLawyerName', label: 'المحامي' },
            { key: 'openedAt', label: 'تاريخ الفتح', format: v => fmt.date(v) }
          ],
          rows: res.items || [],
          onRow: (r) => App.go('financial', { id: r.id }),
          empty: 'لا توجد ملفات مالية مطابقة'
        }));
        listBox.appendChild(pager(res, () => load(), state));
      } catch (e) {
        listBox.innerHTML = '';
        listBox.appendChild(App.empty(e.message || 'تعذّر تحميل الملفات', 'warning'));
      } finally { stop(); }
    }
    await load();
  }

  function pager(res, reload, state) {
    const total = res.total || 0;
    const size = res.size || state.size;
    const pages = Math.max(1, Math.ceil(total / size));
    const box = el('div', 'pager');
    if (pages <= 1) { box.innerHTML = `<span class="muted">${total} سجل</span>`; return box; }
    const prev = el('button', 'btn btn-sm btn-ghost', 'السابق');
    const next = el('button', 'btn btn-sm btn-ghost', 'التالي');
    prev.disabled = state.page <= 0;
    next.disabled = state.page >= pages - 1;
    prev.onclick = () => { state.page--; reload(); };
    next.onclick = () => { state.page++; reload(); };
    box.appendChild(next);
    box.appendChild(el('span', 'muted', `صفحة ${state.page + 1} من ${pages} — ${total} سجل`));
    box.appendChild(prev);
    return box;
  }

  // ===================== إنشاء ملف =====================

  function openCreate() {
    const f = App.form([
      { key: 'clientId', label: 'الموكل', type: 'select', required: true, options: App.people('clients') },
      { key: 'debtorId', label: 'المدين', type: 'select', required: true, options: App.people('debtors') },
      { key: 'claimAmount', label: 'قيمة المطالبة', type: 'money', required: true },
      { key: 'assignedLawyerId', label: 'المحامي المسند', type: 'select', options: App.people('lawyers') },
      { key: 'subject', label: 'الموضوع', type: 'text', required: true, col: 2 },
      { key: 'description', label: 'الوصف', type: 'textarea' },
      { key: 'claimFile', label: 'مستند المطالبة المالية', type: 'file', required: true, col: 2,
        hint: 'إلزامي — لا يُفتح الملف المالي دون إرفاق مستند المطالبة' }
    ], { openedAt: fmt.today() });

    const body = el('div');
    body.appendChild(el('div', 'note',
      'فتح الملف يتطلب إرفاق مستند المطالبة المالية. يُرفع المستند أولاً ثم يُنشأ الملف مرتبطاً به.'));
    body.appendChild(f.node);

    App.modal({
      title: 'ملف مالي جديد',
      width: 'wide',
      bodyNode: body,
      actions: [
        { label: 'فتح الملف', type: 'gold', onClick: async () => {
            const v = f.read();
            const file = v.claimFile;
            if (!file) throw { message: 'مستند المطالبة المالية مطلوب', blockers: [] };

            // ١) رفع مستند المطالبة مؤقتاً
            const fd = new FormData();
            fd.append('file', file);
            fd.append('entityType', 'FINANCIAL_FILE');
            fd.append('entityId', '0');
            fd.append('category', 'مطالبة');
            fd.append('description', 'مستند المطالبة المالية');
            const up = await App.api.upload('/api/attachments', fd);
            const attId = up && up.data ? up.data.id : null;

            // ٢) إنشاء الملف مرتبطاً بالمرفق
            const res = await App.api.post('/api/financial', {
              clientId: v.clientId, debtorId: v.debtorId,
              assignedLawyerId: v.assignedLawyerId,
              claimAmount: v.claimAmount, subject: v.subject,
              description: v.description, openedAt: fmt.today(),
              attachmentIds: attId ? [attId] : []
            });
            App.closeModal();
            App.toast(res.message || 'تم فتح الملف', 'success');
            const id = res.data && res.data.file ? res.data.file.id : null;
            if (id) App.go('financial', { id }); else App.route();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ===================== التفاصيل =====================

  async function renderDetail(root, id) {
    const d = await App.api.get('/api/financial/' + id);
    const file = d.file;

    const head = el('div', 'page-head');
    head.innerHTML = `<div>
        <h2>الملف المالي ${esc(file.fileNumber)} ${App.badge(file.statusLabel, statusTone(file.status))}</h2>
        <p class="page-sub">${esc(file.clientName)} ضد ${esc(file.debtorName)} — ${esc(file.subject || '')}</p>
      </div>`;
    const back = el('button', 'btn btn-ghost', '← رجوع للقائمة');
    back.onclick = () => App.go('financial');
    head.appendChild(back);
    root.appendChild(head);

    if (file.closedAt) {
      root.appendChild(el('div', 'lock-bar',
        `<span class="lock-icon">${App.icon('lock', 18)}</span><span>ملف مغلق (${esc(file.closureTypeLabel || '')}) — لا يقبل التعديل</span>`));
    }

    // مؤشرات مالية
    const metrics = el('div', 'metrics');
    metrics.appendChild(metric('قيمة المطالبة', fmt.money(file.claimAmount), ''));
    metrics.appendChild(metric('المحصّل (دفعات مؤكدة)', fmt.money(file.paidAmount), 'ok'));
    metrics.appendChild(metric('المتبقي', fmt.money(d.remainingAmount), 'danger'));
    root.appendChild(metrics);

    const pct = Number(file.claimAmount) > 0
      ? Math.min(100, Math.round(Number(file.paidAmount) / Number(file.claimAmount) * 100)) : 0;
    const prog = el('div');
    prog.innerHTML = `<div class="row between mb-1"><strong>نسبة التحصيل</strong>
      <strong class="num">${pct}%</strong></div>
      <div class="progress"><span style="width:${pct}%"></span></div>`;
    root.appendChild(App.section(null, prog));

    // التبويبات
    const tabs = el('div', 'tabs');
    const pane = el('div');
    const list = [
      ['data', 'البيانات'],
      ['payments', 'الدفعات'],
      ['plan', 'التقسيط'],
      ['comms', 'التواصل والإنذارات'],
      ['files', 'المرفقات'],
      ['close', 'الإغلاق']
    ];
    list.forEach(([k, label], i) => {
      const b = el('button', 'tab' + (i === 0 ? ' active' : ''), esc(label));
      b.onclick = () => {
        tabs.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        b.classList.add('active');
        drawTab(k);
      };
      tabs.appendChild(b);
    });
    root.appendChild(tabs);
    root.appendChild(pane);

    const reload = () => App.route();

    function drawTab(k) {
      pane.innerHTML = '';
      if (k === 'data') pane.appendChild(tabData(d));
      if (k === 'payments') pane.appendChild(tabPayments(d, reload));
      if (k === 'plan') pane.appendChild(tabPlan(d, reload));
      if (k === 'comms') pane.appendChild(tabComms(d, reload));
      if (k === 'files') pane.appendChild(tabFiles(d, reload));
      if (k === 'close') pane.appendChild(tabClose(d, reload));
    }
    drawTab('data');
  }

  function metric(label, value, tone) {
    const c = el('div', 'metric ' + (tone || ''));
    c.innerHTML = `<div class="metric-label">${esc(label)}</div>
      <div class="metric-value" style="font-size:21px">${esc(value)}</div>`;
    return c;
  }

  // ---------- البيانات ----------
  function tabData(d) {
    const f = d.file;
    const box = el('div');
    const kv = el('div');
    const rows = [
      ['رقم الملف', f.fileNumber], ['الموكل', f.clientName], ['المدين', f.debtorName],
      ['المحامي المسند', f.assignedLawyerName], ['الموضوع', f.subject],
      ['الوصف', f.description], ['تاريخ الفتح', fmt.date(f.openedAt)],
      ['الحالة', f.statusLabel], ['المبلغ المُعفى', fmt.money(f.exemptedAmount)],
      ['وجهة الإنهاء', f.closureTypeLabel], ['ملاحظة الإنهاء', f.closureNote],
      ['أُغلق بواسطة', f.closedByName], ['اعتمد الإعفاء', f.approvedByName]
    ];
    rows.forEach(([k, v]) => {
      if (v === null || v === undefined || v === '') return;
      const r = el('div', 'kv');
      r.style.cssText = 'display:flex;gap:10px;padding:7px 0;border-bottom:1px dotted #eee';
      r.innerHTML = `<b style="min-width:170px;color:var(--navy)">${esc(k)}</b><span>${esc(v)}</span>`;
      kv.appendChild(r);
    });
    box.appendChild(App.section('بيانات الملف', kv));
    box.appendChild(el('div', 'note',
      'تنبيه: حالة الملف تُحتسب تلقائياً من الدفعات المؤكدة صعوداً ونزولاً، ولا تُضبط يدوياً.'));
    return box;
  }

  // ---------- الدفعات ----------
  function tabPayments(d, reload) {
    const box = el('div');
    const actions = [];
    if (d.paymentsAllowed && App.can('FINANCIAL_MANAGE')) {
      actions.push({ label: 'تسجيل دفعة', type: 'gold', onClick: () => openPayment(d, reload) });
    }

    const tbl = App.table({
      columns: [
        { key: 'receiptNumber', label: 'رقم الإيصال' },
        { key: 'amount', label: 'المبلغ', align: 'num', format: v => fmt.money(v) },
        { key: 'paymentDate', label: 'التاريخ', format: v => fmt.date(v) },
        { key: 'methodLabel', label: 'الطريقة' },
        { key: 'statusLabel', label: 'الحالة', align: 'center', format: (v, r) =>
            App.badge(v, r.status === 'CONFIRMED' ? 'ok' : r.status === 'BOUNCED' ? 'danger'
              : r.status === 'CANCELLED' ? 'muted' : 'warn') },
        { key: 'installmentSeq', label: 'القسط', align: 'center', format: v => v ? 'رقم ' + v : '—' },
        { key: 'id', label: 'إجراءات', noSort: true, format: (v, r) => actionCell(r, reload) }
      ],
      rows: d.payments || [],
      empty: 'لا توجد دفعات مسجّلة'
    });

    box.appendChild(App.section('الدفعات', tbl, actions));
    box.appendChild(el('div', 'note',
      'الدفعة تُسجَّل «بانتظار التأكيد» ولا تؤثر على حالة الملف قبل تأكيدها. '
      + 'وارتجاع الشيك يخصم مبلغه ويعيد الأقساط التي سدّدها إلى الاستحقاق.'));
    return box;
  }

  function actionCell(p, reload) {
    const wrap = el('div', 'row');
    wrap.style.gap = '5px';

    if (p.status === 'PENDING' && App.can('PAYMENT_CONFIRM')) {
      const b = el('button', 'btn btn-sm btn-ok', 'تأكيد');
      b.onclick = async (e) => {
        e.stopPropagation();
        if (!await App.confirm('تأكيد الدفعة ' + p.receiptNumber + '؟ ستُحتسب ضمن المحصّل وتُحدَّث حالة الملف.')) return;
        try {
          const r = await App.api.post(`/api/payments/${p.id}/confirm`);
          App.toast(r.message || 'تم التأكيد', 'success');
          reload();
        } catch (err) { App.toastError(err); }
      };
      wrap.appendChild(b);
    }

    if (p.status === 'CONFIRMED' && App.can('PAYMENT_CONFIRM')) {
      const b = el('button', 'btn btn-sm btn-danger', 'ارتجاع');
      b.onclick = (e) => { e.stopPropagation(); openReason(
        'تسجيل ارتجاع الدفعة', 'سبب الارتجاع',
        `/api/payments/${p.id}/bounce`, reload); };
      wrap.appendChild(b);

      const pr = el('button', 'btn btn-sm btn-ghost', 'إيصال');
      pr.onclick = (e) => { e.stopPropagation(); printReceipt(p); };
      wrap.appendChild(pr);
    }

    return wrap.outerHTML ? wrap : wrap;
  }

  function openReason(title, label, path, reload) {
    const f = App.form([{ key: 'reason', label, type: 'textarea', required: true, col: 2 }], {});
    App.modal({
      title, bodyNode: f.node, width: 'narrow',
      actions: [
        { label: 'حفظ', type: 'danger', onClick: async () => {
            const v = f.read();
            const r = await App.api.post(path, { reason: v.reason });
            App.closeModal();
            App.toast(r.message || 'تم', 'success');
            reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  function openPayment(d, reload) {
    const openInst = (d.installments || [])
      .filter(i => i.status !== 'PAID' && i.status !== 'CANCELLED')
      .map(i => ({ value: i.id, label: `القسط ${i.seq} — ${fmt.money(i.amount)} — ${fmt.date(i.dueDate)}` }));

    const fields = [
      { key: 'amount', label: 'المبلغ', type: 'money', required: true },
      { key: 'paymentDate', label: 'تاريخ الدفع', type: 'date', required: true },
      { key: 'method', label: 'طريقة السداد', type: 'select', required: true,
        options: App.options('paymentMethod') },
      { key: 'payerName', label: 'اسم الدافع', type: 'text' },
      { key: 'referenceNo', label: 'رقم الشيك / مرجع التحويل', type: 'text' },
      { key: 'bankName', label: 'البنك', type: 'text' },
      { key: 'notes', label: 'ملاحظات', type: 'textarea' }
    ];
    if (openInst.length) {
      fields.splice(3, 0, { key: 'installmentId', label: 'ربط بقسط بعينه', type: 'select',
        options: openInst, hint: 'اختياري — الربط اليدوي بيد الموظف' });
    }

    const f = App.form(fields, { paymentDate: fmt.today() });
    App.modal({
      title: 'تسجيل دفعة',
      width: 'wide',
      bodyNode: f.node,
      actions: [
        { label: 'حفظ الدفعة', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = await App.api.post(`/api/financial/${d.file.id}/payments`, v);
            App.closeModal();
            App.toast(r.message || 'تم تسجيل الدفعة', 'success');
            reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  function printReceipt(p) {
    App.printHtml('إيصال استلام مبلغ', `
      <div class="kv"><b>رقم الإيصال</b><span>${esc(p.receiptNumber)}</span></div>
      <div class="kv"><b>التاريخ</b><span>${fmt.date(p.paymentDate)}</span></div>
      <div class="kv"><b>المستلَم من</b><span>${esc(p.payerName || '—')}</span></div>
      <div class="kv"><b>طريقة السداد</b><span>${esc(p.methodLabel || '')}</span></div>
      ${p.referenceNo ? `<div class="kv"><b>المرجع</b><span>${esc(p.referenceNo)}</span></div>` : ''}
      ${p.bankName ? `<div class="kv"><b>البنك</b><span>${esc(p.bankName)}</span></div>` : ''}
      <div class="amount-box">المبلغ المستلَم: ${esc(fmt.money(p.amount))}</div>
      <div class="sign-row">
        <div class="sign-box"><div class="sign-line">توقيع المستلم</div></div>
        <div class="sign-box"><div class="sign-line">ختم المكتب</div></div>
      </div>`);
  }

  // ---------- التقسيط ----------
  function tabPlan(d, reload) {
    const box = el('div');
    const actions = [];
    if (!d.file.closedAt && App.can('FINANCIAL_MANAGE')) {
      actions.push({ label: d.plan ? 'خطة تقسيط جديدة' : 'إنشاء خطة تقسيط', type: 'gold',
        onClick: () => openPlan(d, reload) });
    }

    if (!d.plan) {
      box.appendChild(App.section('خطة التقسيط',
        App.empty('لا توجد خطة تقسيط لهذا الملف', 'calendar'), actions));
      return box;
    }

    const info = el('div');
    info.innerHTML = `<div class="row" style="gap:22px;margin-bottom:12px">
      <span><b>الإجمالي:</b> ${esc(fmt.money(d.plan.totalAmount))}</span>
      <span><b>عدد الأقساط:</b> ${d.plan.installmentsCount}</span>
      <span><b>البداية:</b> ${fmt.date(d.plan.startDate)}</span>
      <span><b>الفاصل:</b> ${d.plan.intervalMonths} شهر</span>
      <span><b>الطريقة:</b> ${esc(d.plan.paymentMethodLabel || '—')}</span>
      </div>${d.plan.notes ? `<div class="note">${esc(d.plan.notes)}</div>` : ''}`;

    const tbl = App.table({
      columns: [
        { key: 'seq', label: 'القسط', align: 'center' },
        { key: 'dueDate', label: 'تاريخ الاستحقاق', format: v => fmt.date(v) },
        { key: 'amount', label: 'المبلغ', align: 'num', format: v => fmt.money(v) },
        { key: 'paidAmount', label: 'المسدد', align: 'num', format: v => fmt.money(v) },
        { key: 'statusLabel', label: 'الحالة', align: 'center', format: (v, r) =>
            App.badge(v, r.status === 'PAID' ? 'ok' : r.status === 'OVERDUE' ? 'danger'
              : r.status === 'CANCELLED' ? 'muted' : r.status === 'PARTIAL' ? 'warn' : 'info') },
        { key: 'id', label: 'إجراءات', noSort: true, format: (v, r) => {
            if (r.status === 'PAID' || r.status === 'CANCELLED' || !App.can('FINANCIAL_MANAGE')) return '—';
            const b = el('button', 'btn btn-sm btn-danger', 'إلغاء القسط');
            b.onclick = (e) => { e.stopPropagation(); openReason('إلغاء القسط رقم ' + r.seq,
              'سبب الإلغاء (إلزامي)', `/api/installments/${r.id}/cancel`, reload); };
            return b;
          } }
      ],
      rows: d.installments || [],
      empty: 'لا توجد أقساط'
    });

    const wrap = el('div');
    wrap.appendChild(info);
    wrap.appendChild(tbl);
    box.appendChild(App.section('خطة التقسيط', wrap, actions));
    box.appendChild(el('div', 'note', 'إلغاء القسط إجراء يدوي يتطلب سبباً موثّقاً، ولا يجوز على قسط مسدد بالكامل.'));
    return box;
  }

  function openPlan(d, reload) {
    const preview = el('div');
    const f = App.form([
      { key: 'totalAmount', label: 'إجمالي مبلغ التقسيط', type: 'money', required: true, onChange: () => draw() },
      { key: 'installmentsCount', label: 'عدد الأقساط', type: 'number', required: true, min: 1, onChange: () => draw() },
      { key: 'startDate', label: 'تاريخ أول قسط', type: 'date', required: true, onChange: () => draw() },
      { key: 'intervalMonths', label: 'الفاصل بالأشهر', type: 'number', required: true, min: 1, onChange: () => draw() },
      { key: 'paymentMethod', label: 'طريقة السداد', type: 'select', options: App.options('paymentMethod') },
      { key: 'notes', label: 'التعهد والملاحظات', type: 'textarea' }
    ], { totalAmount: d.remainingAmount, installmentsCount: 6,
         startDate: fmt.today(), intervalMonths: 1 });

    function draw() {
      const total = Number(f.inputs.totalAmount.value || 0);
      const n = Number(f.inputs.installmentsCount.value || 0);
      const start = f.inputs.startDate.value;
      const gap = Number(f.inputs.intervalMonths.value || 1);
      preview.innerHTML = '';
      if (!total || !n || !start || n < 1) return;
      const each = Math.floor(total / n * 100) / 100;
      const rows = [];
      let acc = 0;
      for (let i = 1; i <= n; i++) {
        const dt = new Date(start + 'T00:00:00');
        dt.setMonth(dt.getMonth() + (i - 1) * gap);
        const amt = i === n ? Math.round((total - acc) * 100) / 100 : each;
        acc += each;
        rows.push({ seq: i, due: dt.toISOString().slice(0, 10), amt });
      }
      preview.appendChild(el('div', 'form-hint', 'معاينة جدول الأقساط قبل الحفظ:'));
      preview.appendChild(App.table({
        columns: [
          { key: 'seq', label: 'القسط', align: 'center', noSort: true },
          { key: 'due', label: 'الاستحقاق', noSort: true, format: v => fmt.date(v) },
          { key: 'amt', label: 'المبلغ', align: 'num', noSort: true, format: v => fmt.money(v) }
        ],
        rows
      }));
    }

    const body = el('div');
    body.appendChild(f.node);
    body.appendChild(el('div', 'mb-2'));
    body.appendChild(preview);
    draw();

    App.modal({
      title: 'خطة تقسيط',
      width: 'wide',
      bodyNode: body,
      actions: [
        { label: 'حفظ الخطة', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = await App.api.post(`/api/financial/${d.file.id}/plan`, v);
            App.closeModal();
            App.toast(r.message || 'تم إنشاء الخطة', 'success');
            reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ---------- التواصل ----------
  function tabComms(d, reload) {
    const box = el('div');
    const actions = [];
    if (!d.file.closedAt && App.can('FINANCIAL_MANAGE')) {
      actions.push({ label: 'تسجيل تواصل', type: 'gold', onClick: () => openComm(d, reload) });
    }

    const list = el('ul', 'timeline');
    (d.communications || []).forEach(c => {
      const li = el('li', c.type === 'WARNING' ? 'danger' : '');
      li.innerHTML = `<div class="tl-title">${esc(c.typeLabel)} ${c.referenceNo ? App.badge(c.referenceNo, 'gold') : ''}</div>
        <div>${esc(c.summary)}</div>
        ${c.outcome ? `<div class="muted">النتيجة: ${esc(c.outcome)}</div>` : ''}
        <div class="tl-meta">${fmt.dateTime(c.commDate)}${c.contactPerson ? ' — ' + esc(c.contactPerson) : ''}${c.recordedBy ? ' — سجّله ' + esc(c.recordedBy) : ''}</div>`;
      list.appendChild(li);
    });

    box.appendChild(App.section('سجل التواصل والإنذارات',
      (d.communications || []).length ? list : App.empty('لم يُسجَّل أي تواصل بعد', 'phone'), actions));
    return box;
  }

  function openComm(d, reload) {
    const f = App.form([
      { key: 'type', label: 'نوع التواصل', type: 'select', required: true, options: App.options('commType') },
      { key: 'commDate', label: 'التاريخ والوقت', type: 'datetime', required: true },
      { key: 'contactPerson', label: 'جهة الاتصال', type: 'text' },
      { key: 'referenceNo', label: 'رقم الإنذار (إن وُجد)', type: 'text' },
      { key: 'summary', label: 'الملخص', type: 'textarea', required: true },
      { key: 'outcome', label: 'النتيجة', type: 'textarea' }
    ], { commDate: fmt.today() + 'T10:00' });

    App.modal({
      title: 'تسجيل تواصل أو إنذار',
      width: 'wide',
      bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = await App.api.post(`/api/financial/${d.file.id}/communications`, v);
            App.closeModal();
            App.toast(r.message || 'تم التسجيل', 'success');
            reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ---------- المرفقات ----------
  function tabFiles(d, reload) {
    return attachmentsSection('FINANCIAL_FILE', d.file.id, d.attachments || [], reload, !d.file.closedAt);
  }

  function attachmentsSection(entityType, entityId, items, reload, canEdit) {
    const box = el('div');
    const actions = [];
    if (canEdit) {
      actions.push({ label: 'رفع مرفق', type: 'gold', onClick: () => {
        const f = App.form([
          { key: 'file', label: 'الملف', type: 'file', required: true, col: 2 },
          { key: 'category', label: 'الفئة', type: 'text', hint: 'مثال: مطالبة، إيصال، حكم، إنذار' },
          { key: 'description', label: 'الوصف', type: 'text' }
        ], {});
        App.modal({
          title: 'رفع مرفق', bodyNode: f.node,
          actions: [
            { label: 'رفع', type: 'gold', onClick: async () => {
                const v = f.read();
                const fd = new FormData();
                fd.append('file', v.file);
                fd.append('entityType', entityType);
                fd.append('entityId', entityId);
                if (v.category) fd.append('category', v.category);
                if (v.description) fd.append('description', v.description);
                await App.api.upload('/api/attachments', fd);
                App.closeModal();
                App.toast('تم رفع المرفق', 'success');
                reload();
              } },
            { label: 'إلغاء', onClick: () => App.closeModal() }
          ]
        });
      } });
    }

    const tbl = App.table({
      columns: [
        { key: 'fileName', label: 'اسم الملف' },
        { key: 'category', label: 'الفئة', format: v => v ? App.badge(v, 'gold') : '—' },
        { key: 'description', label: 'الوصف' },
        { key: 'fileSize', label: 'الحجم', align: 'num',
          format: v => v ? (v < 1024 ? v + ' ب' : v < 1048576 ? Math.round(v / 1024) + ' ك.ب'
            : (v / 1048576).toFixed(1) + ' م.ب') : '—' },
        { key: 'createdAt', label: 'تاريخ الرفع', format: v => fmt.dateTime(v) },
        { key: 'id', label: 'إجراءات', noSort: true, format: (v, r) => {
            const w = el('div', 'row');
            w.style.gap = '5px';
            const dl = el('button', 'btn btn-sm btn-ghost', 'تنزيل');
            dl.onclick = (e) => { e.stopPropagation();
              App.api.download(`/api/attachments/${r.id}/download`, r.fileName); };
            w.appendChild(dl);
            if (canEdit) {
              const del = el('button', 'btn btn-sm btn-danger', 'حذف');
              del.onclick = async (e) => {
                e.stopPropagation();
                if (!await App.confirm('حذف المرفق «' + r.fileName + '»؟')) return;
                try { await App.api.del('/api/attachments/' + r.id); App.toast('تم الحذف', 'success'); reload(); }
                catch (err) { App.toastError(err); }
              };
              w.appendChild(del);
            }
            return w;
          } }
      ],
      rows: items,
      empty: 'لا توجد مرفقات'
    });

    box.appendChild(App.section('المرفقات', tbl, actions));
    box.appendChild(el('div', 'note',
      'كل وثيقة تتبع ملفها: لا تُسلَّم إلا لمن يملك حق الاطلاع على الملف الأب.'));
    return box;
  }

  // ---------- الإغلاق ----------
  function tabClose(d, reload) {
    const box = el('div');
    const f = d.file;

    if (f.closedAt) {
      const done = el('div');
      done.appendChild(el('div', 'note ok',
        `أُنهي هذا الملف بوجهة: ${esc(f.closureTypeLabel || '')}`));
      if (f.closureNote) done.appendChild(el('div', null, esc(f.closureNote)));
      box.appendChild(App.section('حالة الإنهاء', done));
      return box;
    }
    if (!App.can('FINANCIAL_MANAGE')) {
      box.appendChild(App.section('الإغلاق', App.empty('لا تملك صلاحية إنهاء الملفات', 'lock')));
      return box;
    }

    const remaining = Number(d.remainingAmount || 0);
    const hasPlan = !!d.plan;
    const hasAttachment = (d.attachments || []).length > 0;

    const options = [
      { type: 'FULL_PAYMENT', title: 'سداد كامل', desc: 'حضور وسداد وتوثيق بإيصال وتأكيد وصول المبلغ.',
        ok: remaining === 0, why: remaining > 0 ? 'غير متاح — المتبقي ' + fmt.money(remaining) : '' },
      { type: 'INSTALLMENT', title: 'سداد بالتقسيط', desc: 'خطة كاملة بالمدة وطرق السداد موثّقة بالتعهد.',
        ok: hasPlan, why: hasPlan ? '' : 'غير متاح — أنشئ خطة تقسيط أولاً' },
      { type: 'LEGAL_OPINION', title: 'رأي قانوني بالإغلاق', desc: 'لمطالبات قديمة أو تعذّر الوصول لصاحبها، بمستندات مثبتة.',
        ok: hasAttachment, why: hasAttachment ? '' : 'غير متاح — أرفق المستندات المثبتة أولاً' },
      { type: 'EXEMPTION', title: 'إعفاء', desc: 'بموافقة المستوى الأعلى، مع إرفاق مستندات الإعفاء.',
        ok: App.can('EXEMPTION_APPROVE') && hasAttachment,
        why: !App.can('EXEMPTION_APPROVE') ? 'غير متاح — يتطلب صلاحية الموافقة على الإعفاء'
          : !hasAttachment ? 'غير متاح — أرفق مستند الإعفاء أولاً' : '' },
      { type: 'ESCALATION', title: 'تصعيد إلى قضية', desc: 'عند رفض السداد — يُفتح ملف قضية بكل المرفقات وسجل التواصل.',
        ok: true, why: '' }
    ];

    const grid = el('div', 'choice-grid');
    options.forEach(o => {
      const c = el('div', 'choice' + (o.ok ? '' : ' disabled'));
      c.innerHTML = `<h4>${esc(o.title)}</h4><p>${esc(o.desc)}</p>
        ${o.why ? `<div class="why">${esc(o.why)}</div>` : ''}`;
      if (o.ok) c.onclick = () => openClose(d, o, reload);
      grid.appendChild(c);
    });

    box.appendChild(App.section('وجهات إنهاء الملف الخمس', grid));
    box.appendChild(el('div', 'note',
      'كل وجهة قرار موثّق ومبرَّر — ولا شيء يُحتسب تلقائياً من المبالغ إلا عبر الدفعات المؤكدة.'));
    return box;
  }

  function openClose(d, opt, reload) {
    const fields = [{ key: 'note', label: 'الملاحظة / المبرر', type: 'textarea',
      required: opt.type === 'LEGAL_OPINION' || opt.type === 'EXEMPTION', col: 2 }];

    const f = App.form(fields, {});
    const body = el('div');
    body.appendChild(el('div', 'note warn', hintOf(opt.type)));
    body.appendChild(f.node);

    App.modal({
      title: 'إنهاء الملف: ' + opt.title,
      bodyNode: body,
      actions: [
        { label: 'تأكيد الإنهاء', type: opt.type === 'ESCALATION' ? 'danger' : 'gold',
          onClick: async () => {
            const v = f.read();
            const r = await App.api.post(`/api/financial/${d.file.id}/close`, {
              closureType: opt.type, note: v.note
            });
            App.closeModal();
            App.toast(r.message || 'تم الإنهاء', 'success');
            const data = r.data || {};
            if (opt.type === 'ESCALATION' && data.caseId) App.go('cases', { id: data.caseId });
            else reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  const hintOf = (t) => ({
    FULL_PAYMENT: 'سيُغلق الملف ويُؤرشف بمستنداته كاملة.',
    INSTALLMENT: 'سيُنهى الملف باعتماد خطة التقسيط النشطة.',
    LEGAL_OPINION: 'يتطلب مبرراً مكتوباً ومستندات مثبتة، وسيُؤرشف الملف.',
    EXEMPTION: 'سيُسجَّل المتبقي كمبلغ مُعفى باسمك، ويُؤرشف الملف.',
    ESCALATION: 'سيُفتح ملف قضية جديد وتُنقل إليه كل المرفقات وسجل التواصل والإنذارات دون إعادة إدخال.'
  }[t] || '');
})();
