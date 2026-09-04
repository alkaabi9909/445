/* =====================================================================
   التنفيذ — الاستيفاء الجبري للحق: خمسة أوامر وبوابة استيفاء صارمة.
   ===================================================================== */

(function () {
  const { el, esc, fmt } = App;

  const ORDER_ICONS = {
    BANK_FREEZE: 'account_balance', TRAVEL_BAN: 'flight', ARREST: 'shield',
    PROPERTY_SEIZURE: 'apartment', SALARY_SEIZURE: 'payments'
  };

  const execTone = (s) => ({
    OPEN: 'info', IN_PROGRESS: 'warn', SATISFIED: 'ok', CLOSED: 'muted'
  }[s] || 'muted');

  App.registerPage('execution', {
    title: 'التنفيذ',
    icon: 'gavel',
    permission: 'EXECUTION_VIEW',
    render: async (root, params) => {
      if (params.id) await renderDetail(root, params.id);
      else await renderList(root);
    }
  });

  // ===================== القائمة =====================

  async function renderList(root) {
    const state = { status: '', q: '', page: 0, size: 20 };

    root.appendChild(el('div', 'page-head',
      `<div><h2>ملفات التنفيذ</h2>
       <p class="page-sub">الاستيفاء الجبري للحق بعد استلام الصيغة التنفيذية</p></div>`));

    const filters = el('div', 'filters');
    const fq = el('label', 'fld grow');
    fq.innerHTML = '<span>بحث</span><input type="text" placeholder="رقم التنفيذ، الموكل، المدين">';
    const fs = el('label', 'fld');
    fs.innerHTML = '<span>الحالة</span>';
    const sel = el('select');
    sel.appendChild(new Option('كل الحالات', ''));
    App.options('execStatus').forEach(o => sel.appendChild(new Option(o.label, o.value)));
    fs.appendChild(sel);
    filters.appendChild(fq);
    filters.appendChild(fs);
    root.appendChild(filters);

    const box = el('div');
    root.appendChild(box);

    let timer = null;
    fq.querySelector('input').addEventListener('input', e => {
      clearTimeout(timer);
      timer = setTimeout(() => { state.q = e.target.value.trim(); state.page = 0; load(); }, 320);
    });
    sel.onchange = () => { state.status = sel.value; state.page = 0; load(); };

    async function load() {
      const stop = App.spinner(box);
      try {
        const res = await App.api.get('/api/executions', state);
        box.innerHTML = '';
        box.appendChild(App.table({
          columns: [
            { key: 'executionNumber', label: 'رقم التنفيذ' },
            { key: 'courtExecutionNumber', label: 'رقم المحكمة' },
            { key: 'clientName', label: 'صاحب الحق' },
            { key: 'debtorName', label: 'المنفَّذ ضده' },
            { key: 'judgmentAmount', label: 'أصل الحكم', align: 'num', format: v => fmt.money(v) },
            { key: 'expensesAmount', label: 'المصروفات', align: 'num', format: v => fmt.money(v) },
            { key: 'collectedAmount', label: 'المحصّل', align: 'num', format: v => fmt.money(v) },
            { key: 'remainingAmount', label: 'المتبقي', align: 'num',
              format: v => Number(v) === 0
                ? App.badge('صفر', 'ok') : `<span class="num">${esc(fmt.money(v))}</span>` },
            { key: 'statusLabel', label: 'الحالة', align: 'center',
              format: (v, r) => App.badge(v, execTone(r.status)) }
          ],
          rows: res.items || [],
          onRow: r => App.go('execution', { id: r.id }),
          empty: 'لا توجد ملفات تنفيذ مطابقة'
        }));
        const total = res.total || 0;
        const pages = Math.max(1, Math.ceil(total / (res.size || state.size)));
        const p = el('div', 'pager');
        if (pages > 1) {
          const prev = el('button', 'btn btn-sm btn-ghost', 'السابق');
          const next = el('button', 'btn btn-sm btn-ghost', 'التالي');
          prev.disabled = state.page <= 0;
          next.disabled = state.page >= pages - 1;
          prev.onclick = () => { state.page--; load(); };
          next.onclick = () => { state.page++; load(); };
          p.appendChild(next);
          p.appendChild(el('span', 'muted', `صفحة ${state.page + 1} من ${pages} — ${total} سجل`));
          p.appendChild(prev);
        } else {
          p.innerHTML = `<span class="muted">${total} سجل</span>`;
        }
        box.appendChild(p);
      } catch (e) {
        box.innerHTML = '';
        box.appendChild(App.empty(e.message || 'تعذّر تحميل ملفات التنفيذ', 'warning'));
      } finally { stop(); }
    }
    await load();
  }

  // ===================== التفاصيل =====================

  async function renderDetail(root, id) {
    const d = await App.api.get('/api/executions/' + id);
    const f = d.file;
    const reload = () => App.route();

    const head = el('div', 'page-head');
    head.innerHTML = `<div>
      <h2>ملف التنفيذ ${esc(f.executionNumber)} ${App.badge(f.statusLabel, execTone(f.status))}</h2>
      <p class="page-sub">${esc(f.clientName)} ضد ${esc(f.debtorName)}${f.caseNumber ? ' — من القضية ' + esc(f.caseNumber) : ''}</p></div>`;
    const back = el('button', 'btn btn-ghost', '← رجوع للقائمة');
    back.onclick = () => App.go('execution');
    head.appendChild(back);
    root.appendChild(head);

    if (f.status === 'CLOSED' || f.satisfiedAt) {
      const bar = el('div', 'lock-bar');
      bar.innerHTML = `<span class="lock-icon"><span class="material-symbols-outlined" style="font-size:18px">check_circle</span></span>
        <span>تم الاستيفاء وأُغلق الملف — شهادة رقم ${esc(f.certificateNumber || '—')}</span>`;
      const p = el('button', 'btn btn-sm btn-gold', 'طباعة شهادة الاستيفاء');
      p.style.marginRight = 'auto';
      p.onclick = () => printCertificate(f.id);
      bar.appendChild(p);
      root.appendChild(bar);
    }

    // مؤشرات
    const m = el('div', 'metrics');
    m.appendChild(metric('أصل الحكم', fmt.money(f.judgmentAmount), ''));
    m.appendChild(metric('المصروفات', fmt.money(f.expensesAmount), ''));
    m.appendChild(metric('المحصّل', fmt.money(f.collectedAmount), 'ok'));
    m.appendChild(metric('المتبقي', fmt.money(f.remainingAmount),
      Number(f.remainingAmount) === 0 ? 'ok' : 'danger'));
    root.appendChild(m);

    const total = Number(f.totalAmount || 0);
    const pct = total > 0 ? Math.min(100, Math.round(Number(f.collectedAmount) / total * 100)) : 0;
    const prog = el('div');
    prog.innerHTML = `<div class="row between mb-1"><strong>تقدم الاستيفاء</strong>
      <strong class="num">${pct}%</strong></div>
      <div class="progress"><span style="width:${pct}%"></span></div>`;
    root.appendChild(App.section(null, prog));

    const tabs = el('div', 'tabs');
    const pane = el('div');
    [['orders', 'أوامر التنفيذ'], ['payments', 'الدفعات'], ['satisfaction', 'بوابة الاستيفاء'],
     ['timeline', 'الخط الزمني'], ['case', 'القضية الأصلية'], ['files', 'المرفقات']]
      .forEach(([k, label], i) => {
        const b = el('button', 'tab' + (i === 0 ? ' active' : ''), esc(label));
        b.onclick = () => {
          tabs.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
          b.classList.add('active');
          draw(k);
        };
        tabs.appendChild(b);
      });
    root.appendChild(tabs);
    root.appendChild(pane);

    function draw(k) {
      pane.innerHTML = '';
      if (k === 'orders') pane.appendChild(tabOrders(d, reload));
      if (k === 'payments') pane.appendChild(tabPayments(d, reload));
      if (k === 'satisfaction') pane.appendChild(tabSatisfaction(d, reload));
      if (k === 'timeline') pane.appendChild(tabTimeline(d));
      if (k === 'case') pane.appendChild(tabCase(d));
      if (k === 'files') pane.appendChild(tabFiles(d, reload));
    }
    draw('orders');
  }

  function metric(label, value, tone) {
    const c = el('div', 'metric ' + (tone || ''));
    c.innerHTML = `<div class="metric-label">${esc(label)}</div>
      <div class="metric-value" style="font-size:20px">${esc(value)}</div>`;
    return c;
  }

  // ---------- أوامر التنفيذ الخمسة ----------
  function tabOrders(d, reload) {
    const box = el('div');
    const open = d.file.status !== 'CLOSED';
    const orders = d.orders || [];

    box.appendChild(el('div', 'note',
      'يجوز إصدار أكثر من أمر تنفيذ ساري في الوقت نفسه، إلى جانب مخاطبات المحكمة اللازمة. '
      + 'وكل أمر يُسجَّل في الخط الزمني للملف بتاريخه وحالته.'));

    const grid = el('div', 'choice-grid');
    App.options('orderType').forEach(t => {
      const mine = orders.filter(o => o.orderType === t.value);
      const active = mine.filter(o => o.active).length;
      const c = el('div', 'choice');
      c.innerHTML = `<h4 style="display:flex;align-items:center;gap:6px"><span class="material-symbols-outlined" style="font-size:20px">${ORDER_ICONS[t.value] || 'description'}</span> ${esc(t.label)}</h4>
        <p>${active ? `<strong>${active}</strong> أمر ساري` : 'لا يوجد أمر ساري'}
        ${mine.length ? ` — ${mine.length} إجمالاً` : ''}</p>`;
      if (open && d.canManage) {
        const b = el('button', 'btn btn-sm btn-gold', 'إصدار أمر');
        b.style.marginTop = '9px';
        b.onclick = () => openOrder(d.file.id, t, reload);
        c.appendChild(b);
      } else {
        c.classList.add('disabled');
      }
      grid.appendChild(c);
    });
    box.appendChild(App.section('الأوامر الخمسة', grid));

    box.appendChild(App.section('سجل الأوامر', App.table({
      columns: [
        { key: 'orderTypeLabel', label: 'النوع' },
        { key: 'orderNumber', label: 'رقم الأمر' },
        { key: 'issuedDate', label: 'تاريخ الإصدار', format: v => fmt.date(v) },
        { key: 'targetEntity', label: 'الجهة المخاطَبة' },
        { key: 'statusLabel', label: 'الحالة', align: 'center',
          format: (v, r) => App.badge(v, r.status === 'ACTIVE' ? 'warn'
            : r.status === 'EXECUTED' ? 'ok' : 'muted') },
        { key: 'cancelReason', label: 'سبب الإلغاء' },
        { key: 'id', label: '', noSort: true, format: (v, r) => {
            if (!r.active || !open || !d.canManage) return '—';
            const b = el('button', 'btn btn-sm btn-danger', 'إلغاء');
            b.onclick = e => { e.stopPropagation(); openCancel(r, reload); };
            return b;
          } }
      ],
      rows: orders,
      empty: 'لم تُصدر أوامر بعد'
    })));
    return box;
  }

  function openOrder(fileId, type, reload) {
    const f = App.form([
      { key: 'orderNumber', label: 'رقم الأمر', type: 'text' },
      { key: 'issuedDate', label: 'تاريخ الإصدار', type: 'date', required: true },
      { key: 'targetEntity', label: 'الجهة المخاطَبة', type: 'text', col: 2,
        hint: 'مثال: مصرف، إدارة الإقامة، دائرة الأراضي، جهة العمل' },
      { key: 'details', label: 'التفاصيل', type: 'textarea' }
    ], { issuedDate: fmt.today() });

    App.modal({
      title: 'إصدار أمر: ' + type.label,
      bodyNode: f.node,
      actions: [
        { label: 'إصدار الأمر', type: 'gold', onClick: async () => {
            const v = f.read();
            v.orderType = type.value;
            const r = await App.api.post(`/api/executions/${fileId}/orders`, v);
            App.closeModal(); App.toast(r.message || 'تم إصدار الأمر', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  function openCancel(order, reload) {
    const f = App.form([{ key: 'reason', label: 'سبب الإلغاء (إلزامي)',
      type: 'textarea', required: true, col: 2 }], {});
    App.modal({
      title: 'إلغاء الأمر: ' + order.orderTypeLabel, bodyNode: f.node, width: 'narrow',
      actions: [
        { label: 'تأكيد الإلغاء', type: 'danger', onClick: async () => {
            const r = await App.api.post(`/api/execution-orders/${order.id}/cancel`, f.read());
            App.closeModal(); App.toast(r.message || 'تم الإلغاء', 'success'); reload();
          } },
        { label: 'تراجع', onClick: () => App.closeModal() }
      ]
    });
  }

  // ---------- الدفعات ----------
  function tabPayments(d, reload) {
    const box = el('div');
    const actions = [];
    if (d.file.status !== 'CLOSED' && d.canManage) {
      actions.push({ label: 'تسجيل دفعة', type: 'gold', onClick: () => openPayment(d.file.id, reload) });
    }
    box.appendChild(App.section('الدفعات المحصّلة', App.table({
      columns: [
        { key: 'receiptNumber', label: 'رقم الإيصال' },
        { key: 'amount', label: 'المبلغ', align: 'num', format: v => fmt.money(v) },
        { key: 'paymentDate', label: 'التاريخ', format: v => fmt.date(v) },
        { key: 'methodLabel', label: 'الطريقة' },
        { key: 'statusLabel', label: 'الحالة', align: 'center',
          format: (v, r) => App.badge(v, r.status === 'CONFIRMED' ? 'ok'
            : r.status === 'BOUNCED' ? 'danger' : 'warn') },
        { key: 'payerName', label: 'الدافع' }
      ],
      rows: d.payments || [],
      empty: 'لا توجد دفعات'
    }), actions));
    return box;
  }

  function openPayment(fileId, reload) {
    const f = App.form([
      { key: 'amount', label: 'المبلغ', type: 'money', required: true },
      { key: 'paymentDate', label: 'تاريخ الدفع', type: 'date', required: true },
      { key: 'method', label: 'طريقة السداد', type: 'select', required: true,
        options: App.options('paymentMethod') },
      { key: 'payerName', label: 'اسم الدافع', type: 'text' },
      { key: 'referenceNo', label: 'المرجع', type: 'text' },
      { key: 'bankName', label: 'البنك', type: 'text' },
      { key: 'notes', label: 'ملاحظات', type: 'textarea' }
    ], { paymentDate: fmt.today() });
    App.modal({
      title: 'تسجيل دفعة على ملف التنفيذ', width: 'wide', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const r = await App.api.post(`/api/executions/${fileId}/payments`, f.read());
            App.closeModal(); App.toast(r.message || 'تم التسجيل', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ---------- بوابة الاستيفاء ----------
  function tabSatisfaction(d, reload) {
    const box = el('div');
    const f = d.file;
    const s = d.satisfaction || { allowed: false, remaining: f.remainingAmount, activeOrders: 0, blockers: [] };

    if (f.status === 'CLOSED') {
      const done = el('div');
      done.appendChild(el('div', 'note ok',
        `اكتمل الاستيفاء بتاريخ ${fmt.dateTime(f.satisfiedAt)} — شهادة رقم ${esc(f.certificateNumber || '')}`));
      const b = el('button', 'btn btn-gold', 'طباعة شهادة الاستيفاء');
      b.onclick = () => printCertificate(f.id);
      done.appendChild(b);
      box.appendChild(App.section('بوابة الاستيفاء', done));
      return box;
    }

    const card = el('div');
    const grid = el('div', 'grid-2');
    grid.appendChild(metric('المتبقي', fmt.money(s.remaining),
      Number(s.remaining) === 0 ? 'ok' : 'danger'));
    grid.appendChild(metric('أوامر سارية', s.activeOrders, s.activeOrders ? 'warn' : ''));
    card.appendChild(grid);

    if (!s.allowed) {
      const n = el('div', 'note danger');
      n.innerHTML = '<strong>الإغلاق ممنوع:</strong><ul style="margin:6px 0 0;padding-inline-start:18px">'
        + (s.blockers || []).map(b => `<li>${esc(b)}</li>`).join('') + '</ul>';
      card.appendChild(n);
    } else {
      card.appendChild(el('div', 'note ok',
        'المتبقي = صفر — يمكن إتمام الاستيفاء وإغلاق الملف.'));
      if (d.canManage) {
        const b = el('button', 'btn btn-gold', 'إتمام الاستيفاء وإغلاق الملف');
        b.onclick = async () => {
          const ok = await App.confirm(
            'سيتم في عملية واحدة: إلغاء كل الأوامر السارية دفعة واحدة، '
            + 'وإصدار شهادة استيفاء مرقّمة، وإغلاق الملف وأرشفته، وقيد ذلك في سجل النشاطات. '
            + 'هل تريد المتابعة؟');
          if (!ok) return;
          try {
            const r = await App.api.post(`/api/executions/${f.id}/satisfy`);
            App.toast(r.message || 'اكتمل الاستيفاء', 'success');
            reload();
          } catch (e) { App.toastError(e); }
        };
        card.appendChild(b);
      }
    }

    box.appendChild(App.section('بوابة الاستيفاء', card));
    box.appendChild(el('div', 'note',
      'لا يمكن الإغلاق إطلاقاً ما لم يكن المتبقي = صفر — ويُعاد فحص الشرط على الخادم قبل التنفيذ.'));
    return box;
  }

  async function printCertificate(id) {
    try {
      const c = await App.api.get(`/api/executions/${id}/certificate`);
      App.printHtml('شهادة استيفاء', `
        <div class="kv"><b>رقم الشهادة</b><span>${esc(c.certificateNumber || '')}</span></div>
        <div class="kv"><b>رقم ملف التنفيذ</b><span>${esc(c.executionNumber || '')}</span></div>
        <div class="kv"><b>رقم المحكمة</b><span>${esc(c.courtExecutionNumber || '—')}</span></div>
        <div class="kv"><b>محكمة التنفيذ</b><span>${esc(c.court || '—')}</span></div>
        <div class="kv"><b>القضية</b><span>${esc(c.caseNumber || '—')}</span></div>
        <div class="kv"><b>الحكم</b><span>${esc(c.judgmentNumber || '—')} بتاريخ ${fmt.date(c.judgmentDate)}</span></div>
        <div class="kv"><b>صاحب الحق</b><span>${esc(c.creditorName || '')}</span></div>
        <div class="kv"><b>المنفَّذ ضده</b><span>${esc(c.debtorName || '')}</span></div>
        <div class="kv"><b>أصل الحكم</b><span>${esc(fmt.money(c.judgmentAmount))}</span></div>
        <div class="kv"><b>المصروفات</b><span>${esc(fmt.money(c.expensesAmount))}</span></div>
        <div class="amount-box">إجمالي المستوفى: ${esc(fmt.money(c.collectedAmount))}</div>
        <p style="margin-top:18px;line-height:2">
          تشهد إدارة المكتب بأن المبلغ المحكوم به في الملف المشار إليه أعلاه قد
          <strong>استُوفي بالكامل</strong>، وأنه لم يبقَ في ذمة المنفَّذ ضده أي مبلغ بموجب هذا التنفيذ،
          وقد أُلغيت جميع أوامر التنفيذ السارية تبعاً لذلك.
        </p>
        <div class="sign-row">
          <div class="sign-box"><div class="sign-line">مدير المكتب</div></div>
          <div class="sign-box"><div class="sign-line">ختم المكتب</div></div>
        </div>`);
    } catch (e) { App.toastError(e); }
  }

  // ---------- الخط الزمني ----------
  function tabTimeline(d) {
    const box = el('div');
    const items = d.timeline || [];
    if (!items.length) {
      box.appendChild(App.section('الخط الزمني', App.empty('لا توجد إجراءات مسجّلة', 'schedule')));
      return box;
    }
    const list = el('ul', 'timeline');
    items.forEach(t => {
      const li = el('li', t.status === 'CANCELLED' ? 'danger' : t.status === 'CLOSED' ? 'done' : '');
      li.innerHTML = `<div class="tl-title">${esc(t.title)}
          ${t.statusLabel ? App.badge(t.statusLabel, t.status === 'ACTIVE' ? 'warn' : 'muted') : ''}</div>
        ${t.detail ? `<div>${esc(t.detail)}</div>` : ''}
        <div class="tl-meta">${esc(t.kindLabel || '')} — ${fmt.dateTime(t.at)}</div>`;
      list.appendChild(li);
    });
    box.appendChild(App.section('الخط الزمني للملف', list));
    return box;
  }

  // ---------- القضية الأصلية ----------
  function tabCase(d) {
    const box = el('div');
    const c = d.sourceCase;
    if (!c) {
      box.appendChild(App.section('القضية الأصلية', App.empty('لا توجد قضية مرتبطة', 'balance')));
      return box;
    }
    const kv = el('div');
    [['رقم القضية', c.caseNumber], ['رقم المحكمة', c.courtCaseNumber], ['المحكمة', c.court],
     ['النوع', c.caseTypeLabel], ['الموضوع', c.subject], ['الحالة', c.statusLabel],
     ['رقم الحكم', c.judgmentNumber], ['تاريخ الحكم', fmt.date(c.judgmentDate)],
     ['لصالح', c.judgmentForLabel], ['المبلغ المحكوم به', c.judgmentAmount ? fmt.money(c.judgmentAmount) : null],
     ['موسوم نهائياً', c.judgmentFinal ? 'نعم' : 'لا'], ['المحامي', c.assignedLawyerName],
     ['ملخص الحكم', c.judgmentSummary]
    ].forEach(([k, v]) => {
      if (v === null || v === undefined || v === '') return;
      const r = el('div');
      r.style.cssText = 'display:flex;gap:10px;padding:7px 0;border-bottom:1px dotted #eee';
      r.innerHTML = `<b style="min-width:170px;color:var(--navy)">${esc(k)}</b><span>${esc(v)}</span>`;
      kv.appendChild(r);
    });
    const open = el('button', 'btn btn-ghost', 'فتح صفحة القضية');
    open.onclick = () => App.go('cases', { id: c.id });
    box.appendChild(App.section('القضية الأصلية', kv, [open]));
    return box;
  }

  // ---------- المرفقات ----------
  function tabFiles(d, reload) {
    const box = el('div');
    const actions = [];
    if (d.canManage && d.file.status !== 'CLOSED') {
      actions.push({ label: 'رفع مرفق', type: 'gold',
        onClick: () => App.uploadTo('EXECUTION', d.file.id, reload) });
    }
    box.appendChild(App.section('المرفقات', App.table({
      columns: [
        { key: 'fileName', label: 'اسم الملف' },
        { key: 'category', label: 'الفئة', format: v => v ? App.badge(v, 'gold') : '—' },
        { key: 'description', label: 'الوصف' },
        { key: 'createdAt', label: 'تاريخ الرفع', format: v => fmt.dateTime(v) },
        { key: 'id', label: '', noSort: true, format: (v, r) => {
            const b = el('button', 'btn btn-sm btn-ghost', 'تنزيل');
            b.onclick = e => { e.stopPropagation();
              App.api.download(`/api/attachments/${r.id}/download`, r.fileName); };
            return b;
          } }
      ],
      rows: d.attachments || [],
      empty: 'لا توجد مرفقات'
    }), actions));
    return box;
  }
})();
