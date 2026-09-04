/* =====================================================================
   المسار الثاني: القضايا — الجلسات، الحكم، الاستئناف، وبوابة التحويل للتنفيذ.
   ===================================================================== */

(function () {
  const { el, esc, fmt } = App;

  const caseTone = (s) => ({
    OPEN: 'info', IN_PROGRESS: 'info', JUDGED: 'gold',
    APPEALED: 'warn', TRANSFERRED: 'ok', CLOSED: 'muted'
  }[s] || 'muted');

  App.registerPage('cases', {
    title: 'القضايا',
    icon: 'balance',
    permission: 'CASE_VIEW',
    render: async (root, params) => {
      if (params.id) await renderDetail(root, params.id);
      else await renderList(root);
    }
  });

  // ===================== القائمة =====================

  async function renderList(root) {
    const state = { status: '', q: '', page: 0, size: 20 };

    const head = el('div', 'page-head');
    head.innerHTML = `<div><h2>القضايا</h2>
      <p class="page-sub">من رفع الدعوى حتى الحكم والتحويل للتنفيذ</p></div>`;
    if (App.can('CASE_MANAGE')) {
      const b = el('button', 'btn btn-gold', 'قضية جديدة');
      b.onclick = () => openCreate();
      head.appendChild(b);
    }
    root.appendChild(head);

    const filters = el('div', 'filters');
    const fq = el('label', 'fld grow');
    fq.innerHTML = '<span>بحث</span><input type="text" placeholder="رقم القضية، المحكمة، الموكل، الخصم">';
    const fs = el('label', 'fld');
    fs.innerHTML = '<span>الحالة</span>';
    const sel = el('select');
    sel.appendChild(new Option('كل الحالات', ''));
    App.options('caseStatus').forEach(o => sel.appendChild(new Option(o.label, o.value)));
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
        const res = await App.api.get('/api/cases', state);
        box.innerHTML = '';
        box.appendChild(App.table({
          columns: [
            { key: 'caseNumber', label: 'رقم القضية' },
            { key: 'courtCaseNumber', label: 'رقم المحكمة' },
            { key: 'court', label: 'المحكمة' },
            { key: 'caseTypeLabel', label: 'النوع' },
            { key: 'clientName', label: 'الموكل' },
            { key: 'opponentName', label: 'الخصم' },
            { key: 'statusLabel', label: 'الحالة', align: 'center',
              format: (v, r) => App.badge(v, caseTone(r.status)) },
            { key: 'judgmentDate', label: 'تاريخ الحكم', format: v => fmt.date(v) },
            { key: 'appealDeadline', label: 'أجل الطعن', format: (v, r) => deadlineCell(v, r) },
            { key: 'assignedLawyerName', label: 'المحامي' }
          ],
          rows: res.items || [],
          onRow: r => App.go('cases', { id: r.id }),
          empty: 'لا توجد قضايا مطابقة'
        }));
        box.appendChild(pager(res, load, state));
      } catch (e) {
        box.innerHTML = '';
        box.appendChild(App.empty(e.message || 'تعذّر تحميل القضايا', 'warning'));
      } finally { stop(); }
    }
    await load();
  }

  /** عدّاد الأيام المتبقية في أجل الطعن بلون تحذيري. */
  function deadlineCell(v, r) {
    if (!v) return '—';
    if (r && r.executionFileId) return fmt.date(v);
    const days = fmt.daysFromToday(v);
    const label = fmt.date(v);
    if (days === null) return label;
    if (days < 0) return `${label} ${App.badge('انقضى', 'ok')}`;
    if (days === 0) return `${label} ${App.badge('آخر يوم', 'danger')}`;
    if (days <= 7) return `${label} ${App.badge('متبقٍ ' + days + ' يوم', 'danger')}`;
    return `${label} ${App.badge('متبقٍ ' + days + ' يوم', 'warn')}`;
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

  function openCreate() {
    const f = App.form([
      { key: 'clientId', label: 'الموكل', type: 'select', required: true, options: App.people('clients') },
      { key: 'opponentId', label: 'الخصم', type: 'select', required: true, options: App.people('debtors') },
      { key: 'caseType', label: 'نوع القضية', type: 'select', required: true, options: App.options('caseType') },
      { key: 'assignedLawyerId', label: 'المحامي المسند', type: 'select', options: App.people('lawyers') },
      { key: 'court', label: 'المحكمة', type: 'text' },
      { key: 'courtCaseNumber', label: 'رقم القضية لدى المحكمة', type: 'text' },
      { key: 'claimAmount', label: 'قيمة المطالبة', type: 'money' },
      { key: 'filedAt', label: 'تاريخ رفع الدعوى', type: 'date' },
      { key: 'subject', label: 'الموضوع', type: 'text', required: true, col: 2 },
      { key: 'description', label: 'الوصف', type: 'textarea' },
      { key: 'assignmentReason', label: 'سبب الإسناد', type: 'text', col: 2 }
    ], { filedAt: fmt.today() });

    App.modal({
      title: 'قضية جديدة', width: 'wide', bodyNode: f.node,
      actions: [
        { label: 'فتح القضية', type: 'gold', onClick: async () => {
            const v = f.read();
            v.openedAt = fmt.today();
            const r = await App.api.post('/api/cases', v);
            App.closeModal();
            App.toast(r.message || 'تم فتح القضية', 'success');
            const id = r.data && r.data.case ? r.data.case.id : null;
            if (id) App.go('cases', { id }); else App.route();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ===================== التفاصيل =====================

  async function renderDetail(root, id) {
    const d = await App.api.get('/api/cases/' + id);
    const c = d.case;
    const reload = () => App.route();

    const head = el('div', 'page-head');
    head.innerHTML = `<div>
        <h2>القضية ${esc(c.caseNumber)} ${App.badge(statusLabel(c.status), caseTone(c.status))}</h2>
        <p class="page-sub">${esc(c.client ? c.client.name : '')} ضد ${esc(c.opponent ? c.opponent.name : '')} — ${esc(c.subject || '')}</p>
      </div>`;
    const back = el('button', 'btn btn-ghost', '← رجوع للقائمة');
    back.onclick = () => App.go('cases');
    head.appendChild(back);
    root.appendChild(head);

    if (c.sourceFinancialFileId) {
      const n = el('div', 'note');
      n.innerHTML = `هذه القضية مصعّدة من ملف مالي — نُقلت إليها المرفقات وسجل التواصل والإنذارات.
        <button class="link-btn" id="go-src">فتح الملف المالي الأصلي</button>`;
      root.appendChild(n);
      n.querySelector('#go-src').onclick = () => App.go('financial', { id: c.sourceFinancialFileId });
    }

    if (c.appealDeadline && !c.executionFileId) {
      const days = fmt.daysFromToday(c.appealDeadline);
      if (days !== null && days >= 0) {
        root.appendChild(el('div', days <= 7 ? 'note danger' : 'note warn',
          days === 0
            ? `اليوم هو آخر يوم في أجل الطعن (${fmt.date(c.appealDeadline)}) — التحويل للتنفيذ لا يجوز إلا من الغد.`
            : `أجل الطعن ينتهي في ${fmt.date(c.appealDeadline)} — متبقٍ ${days} يوماً.`));
      }
    }

    const tabs = el('div', 'tabs');
    const pane = el('div');
    [['data', 'البيانات'], ['hearings', 'الجلسات'], ['judgment', 'الحكم'],
     ['appeals', 'الاستئناف'], ['transfer', 'التحويل للتنفيذ'],
     ['comms', 'سجل التواصل'], ['files', 'المرفقات']]
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
      if (k === 'data') pane.appendChild(tabData(d, reload));
      if (k === 'hearings') pane.appendChild(tabHearings(d, reload));
      if (k === 'judgment') pane.appendChild(tabJudgment(d, reload));
      if (k === 'appeals') pane.appendChild(tabAppeals(d, reload));
      if (k === 'transfer') pane.appendChild(tabTransfer(d, reload));
      if (k === 'comms') pane.appendChild(tabComms(d));
      if (k === 'files') pane.appendChild(tabFiles(d, reload));
    }
    draw('data');
  }

  const statusLabel = (s) => App.labelOf('caseStatus', s);

  function kvBlock(rows) {
    const kv = el('div');
    rows.forEach(([k, v]) => {
      if (v === null || v === undefined || v === '') return;
      const r = el('div');
      r.style.cssText = 'display:flex;gap:10px;padding:7px 0;border-bottom:1px dotted #eee';
      r.innerHTML = `<b style="min-width:180px;color:var(--navy)">${esc(k)}</b><span>${esc(v)}</span>`;
      kv.appendChild(r);
    });
    return kv;
  }

  // ---------- البيانات والإسناد ----------
  function tabData(d, reload) {
    const c = d.case;
    const box = el('div');
    const actions = [];
    if (App.can('CASE_ASSIGN')) {
      actions.push({ label: 'إسناد لمحامٍ', type: 'gold', onClick: () => openAssign(c, reload) });
    }
    box.appendChild(App.section('بيانات القضية', kvBlock([
      ['رقم القضية', c.caseNumber], ['رقم المحكمة', c.courtCaseNumber], ['المحكمة', c.court],
      ['النوع', App.labelOf('caseType', c.caseType)],
      ['الموكل', c.client ? c.client.name : null], ['الخصم', c.opponent ? c.opponent.name : null],
      ['الموضوع', c.subject], ['الوصف', c.description],
      ['قيمة المطالبة', c.claimAmount ? fmt.money(c.claimAmount) : null],
      ['تاريخ الفتح', fmt.date(c.openedAt)], ['تاريخ رفع الدعوى', fmt.date(c.filedAt)],
      ['المحامي المسند', c.assignedLawyer ? c.assignedLawyer.fullName : null],
      ['سبب الإسناد', c.assignmentReason]
    ]), actions));
    return box;
  }

  function openAssign(c, reload) {
    const f = App.form([
      { key: 'lawyerId', label: 'المحامي', type: 'select', required: true, options: App.people('lawyers') },
      { key: 'reason', label: 'سبب الإسناد (إلزامي)', type: 'textarea', required: true, col: 2 }
    ], { lawyerId: c.assignedLawyer ? c.assignedLawyer.id : '' });
    App.modal({
      title: 'إسناد القضية', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = await App.api.post(`/api/cases/${c.id}/assign`, v);
            App.closeModal(); App.toast(r.message || 'تم الإسناد', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ---------- الجلسات ----------
  function tabHearings(d, reload) {
    const box = el('div');
    const actions = [];
    if (App.can('CASE_MANAGE')) {
      actions.push({ label: 'تسجيل جلسة', type: 'gold', onClick: () => openHearing(d.case.id, null, reload) });
    }

    const rows = (d.hearings || []).map(h => Object.assign({}, h,
      { typeLabel: App.labelOf('hearingType', h.type) }));

    box.appendChild(App.section('الجلسات', App.table({
      columns: [
        { key: 'hearingDate', label: 'التاريخ', format: v => fmt.date(v) },
        { key: 'hearingTime', label: 'الوقت', format: v => v ? String(v).slice(0, 5) : '—' },
        { key: 'typeLabel', label: 'النوع' },
        { key: 'court', label: 'المحكمة' },
        { key: 'room', label: 'القاعة' },
        { key: 'attended', label: 'الحضور', align: 'center',
          format: v => v ? App.badge('حضر', 'ok') : App.badge('لم يُسجَّل', 'muted') },
        { key: 'result', label: 'ما جرى' },
        { key: 'id', label: '', noSort: true, format: (v, r) => {
            if (!App.can('CASE_MANAGE')) return '—';
            const b = el('button', 'btn btn-sm btn-ghost', 'تعديل');
            b.onclick = e => { e.stopPropagation(); openHearing(d.case.id, r, reload); };
            return b;
          } }
      ],
      rows,
      empty: 'لا توجد جلسات مسجّلة'
    }), actions));
    return box;
  }

  function openHearing(caseId, h, reload) {
    const f = App.form([
      { key: 'hearingDate', label: 'تاريخ الجلسة', type: 'date', required: true },
      { key: 'hearingTime', label: 'الوقت', type: 'text', hint: 'مثال 09:30' },
      { key: 'type', label: 'نوع الجلسة', type: 'select', required: true, options: App.options('hearingType') },
      { key: 'court', label: 'المحكمة', type: 'text' },
      { key: 'room', label: 'القاعة', type: 'text' },
      { key: 'nextHearingDate', label: 'الجلسة القادمة', type: 'date' },
      { key: 'attended', label: 'تم الحضور', type: 'checkbox' },
      { key: 'result', label: 'ما جرى في الجلسة', type: 'textarea' },
      { key: 'notes', label: 'ملاحظات', type: 'textarea' }
    ], h ? {
      hearingDate: h.hearingDate, hearingTime: h.hearingTime ? String(h.hearingTime).slice(0, 5) : '',
      type: h.type, court: h.court, room: h.room, nextHearingDate: h.nextHearingDate,
      attended: h.attended, result: h.result, notes: h.notes
    } : { hearingDate: fmt.today() });

    App.modal({
      title: h ? 'تعديل الجلسة' : 'تسجيل جلسة', width: 'wide', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const v = f.read();
            if (v.hearingTime && /^\d{1,2}:\d{2}$/.test(v.hearingTime)) {
              v.hearingTime = v.hearingTime.length === 4 ? '0' + v.hearingTime : v.hearingTime;
              v.hearingTime += ':00';
            } else { v.hearingTime = null; }
            const r = h
              ? await App.api.put(`/api/hearings/${h.id}`, v)
              : await App.api.post(`/api/cases/${caseId}/hearings`, v);
            App.closeModal(); App.toast(r.message || 'تم الحفظ', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ---------- الحكم ----------
  function tabJudgment(d, reload) {
    const c = d.case;
    const box = el('div');
    const actions = [];
    if (App.can('CASE_MANAGE') && !c.executionFileId) {
      actions.push({ label: c.judgmentDate ? 'تعديل بيانات الحكم' : 'توثيق الحكم', type: 'gold',
        onClick: () => openJudgment(c, reload) });
    }

    if (!c.judgmentDate) {
      box.appendChild(App.section('الحكم', App.empty('لم يُوثَّق حكم لهذه القضية بعد', 'receipt_long'), actions));
      return box;
    }

    const info = kvBlock([
      ['تاريخ الحكم', fmt.date(c.judgmentDate)],
      ['رقم الحكم', c.judgmentNumber],
      ['لصالح من', App.labelOf('judgmentFor', c.judgmentFor)],
      ['المبلغ المحكوم به', c.judgmentAmount ? fmt.money(c.judgmentAmount) : null],
      ['موسوم نهائياً صراحة', c.judgmentFinal ? 'نعم' : 'لا'],
      ['مدة الطعن المطبّقة', c.appealDays ? c.appealDays + ' يوماً' : null],
      ['أجل الطعن ينتهي في', fmt.date(c.appealDeadline)],
      ['ملخص الحكم', c.judgmentSummary]
    ]);

    const calc = el('div', 'note');
    calc.innerHTML = `<strong>حساب أجل الطعن:</strong> تاريخ الحكم (${fmt.date(c.judgmentDate)})
      + مدة الطعن القانونية (${c.appealDays || 30} يوماً) =
      <strong>ينتهي أجل الطعن في ${fmt.date(c.appealDeadline)}</strong>.
      واليوم الأخير نفسه لا يزال ضمن المهلة — فالتحويل للتنفيذ لا يجوز إلا من اليوم التالي.`;

    const wrap = el('div');
    wrap.appendChild(info);
    wrap.appendChild(el('div', 'mb-2'));
    wrap.appendChild(calc);
    box.appendChild(App.section('الحكم', wrap, actions));
    return box;
  }

  function openJudgment(c, reload) {
    const f = App.form([
      { key: 'judgmentDate', label: 'تاريخ الحكم', type: 'date', required: true },
      { key: 'judgmentNumber', label: 'رقم الحكم', type: 'text', required: true },
      { key: 'judgmentFor', label: 'لصالح من صدر', type: 'select', required: true,
        options: App.options('judgmentFor') },
      { key: 'judgmentAmount', label: 'المبلغ المحكوم به', type: 'money' },
      { key: 'judgmentFinal', label: 'موسوم نهائياً صراحة', type: 'checkbox', col: 2,
        hint: 'فعّلها فقط إذا نصّ الحكم صراحةً على أنه نهائي' },
      { key: 'judgmentSummary', label: 'ملخص الحكم', type: 'textarea' }
    ], c.judgmentDate ? {
      judgmentDate: c.judgmentDate, judgmentNumber: c.judgmentNumber,
      judgmentFor: c.judgmentFor, judgmentAmount: c.judgmentAmount,
      judgmentFinal: c.judgmentFinal, judgmentSummary: c.judgmentSummary
    } : { judgmentDate: fmt.today() });

    const body = el('div');
    body.appendChild(el('div', 'note',
      'عند الحفظ يحسب النظام أجل الطعن آلياً = تاريخ الحكم + مدة الطعن القانونية، وينبّه قبل انتهائه.'));
    body.appendChild(f.node);

    App.modal({
      title: 'توثيق الحكم', width: 'wide', bodyNode: body,
      actions: [
        { label: 'حفظ الحكم', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = await App.api.post(`/api/cases/${c.id}/judgment`, v);
            App.closeModal(); App.toast(r.message || 'تم توثيق الحكم', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ---------- الاستئناف ----------
  function tabAppeals(d, reload) {
    const box = el('div');
    const actions = [];
    if (App.can('CASE_MANAGE')) {
      actions.push({ label: 'تسجيل استئناف', type: 'gold', onClick: () => openAppeal(d.case.id, reload) });
    }

    const rows = (d.appeals || []).map(a => Object.assign({}, a, {
      statusLabel: App.labelOf('appealStatus', a.status),
      filedByLabel: App.labelOf('appealBy', a.filedBy)
    }));

    box.appendChild(App.section('الاستئنافات', App.table({
      columns: [
        { key: 'appealNumber', label: 'رقم الاستئناف' },
        { key: 'filedDate', label: 'تاريخ التقديم', format: v => fmt.date(v) },
        { key: 'filedByLabel', label: 'مقدَّم من' },
        { key: 'court', label: 'المحكمة' },
        { key: 'statusLabel', label: 'الحالة', align: 'center',
          format: (v, r) => App.badge(v, r.status === 'PENDING' ? 'danger' : 'muted') },
        { key: 'decisionDate', label: 'تاريخ الفصل', format: v => fmt.date(v) },
        { key: 'id', label: '', noSort: true, format: (v, r) => {
            if (r.status !== 'PENDING' || !App.can('CASE_MANAGE')) return '—';
            const b = el('button', 'btn btn-sm btn-ok', 'الفصل فيه');
            b.onclick = e => { e.stopPropagation(); openDecide(r, reload); };
            return b;
          } }
      ],
      rows,
      empty: 'لا توجد استئنافات مسجّلة'
    }), actions));

    box.appendChild(el('div', 'note warn',
      'وجود استئناف مُعلَّق واحد يمنع التحويل للتنفيذ نهائياً — حتى لو انقضى أجل الطعن.'));
    return box;
  }

  function openAppeal(caseId, reload) {
    const f = App.form([
      { key: 'appealNumber', label: 'رقم الاستئناف', type: 'text' },
      { key: 'filedDate', label: 'تاريخ التقديم', type: 'date', required: true },
      { key: 'filedBy', label: 'مقدَّم من', type: 'select', required: true, options: App.options('appealBy') },
      { key: 'court', label: 'محكمة الاستئناف', type: 'text' },
      { key: 'notes', label: 'ملاحظات', type: 'textarea' }
    ], { filedDate: fmt.today() });
    App.modal({
      title: 'تسجيل استئناف', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const r = await App.api.post(`/api/cases/${caseId}/appeals`, f.read());
            App.closeModal(); App.toast(r.message || 'تم التسجيل', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  function openDecide(a, reload) {
    const f = App.form([
      { key: 'status', label: 'النتيجة', type: 'select', required: true,
        options: App.options('appealStatus').filter(o => o.value !== 'PENDING') },
      { key: 'decisionDate', label: 'تاريخ الفصل', type: 'date', required: true },
      { key: 'decisionSummary', label: 'ملخص القرار', type: 'textarea' }
    ], { decisionDate: fmt.today() });
    App.modal({
      title: 'الفصل في الاستئناف', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const r = await App.api.post(`/api/appeals/${a.id}/decide`, f.read());
            App.closeModal(); App.toast(r.message || 'تم الحفظ', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // ---------- بوابة التحويل: الشروط الستة ----------
  function tabTransfer(d, reload) {
    const c = d.case;
    const box = el('div');

    if (c.executionFileId) {
      const done = el('div');
      done.appendChild(el('div', 'note ok', 'سبق تحويل هذه القضية إلى ملف تنفيذ.'));
      const b = el('button', 'btn btn-gold', 'فتح ملف التنفيذ');
      b.onclick = () => App.go('execution', { id: c.executionFileId });
      done.appendChild(b);
      box.appendChild(App.section('التحويل للتنفيذ', done));
      return box;
    }

    const check = d.transferCheck || { allowed: false, checks: [] };
    const list = el('ul', 'checks');
    (check.checks || []).forEach((k, i) => {
      const li = el('li', k.passed ? 'pass' : 'fail');
      li.innerHTML = `<span class="check-mark">${k.passed ? '✓' : '✗'}</span>
        <div><div class="check-label">${i + 1}. ${esc(k.label)}</div>
        ${!k.passed && k.reason ? `<div class="check-reason">${esc(k.reason)}</div>` : ''}</div>`;
      list.appendChild(li);
    });

    const wrap = el('div');
    wrap.appendChild(el('div', 'note',
      'زر «تأكيد التحويل» لا يظهر إلا باستيفاء الشروط الستة كاملة — وتُفحص مرة ثانية على الخادم قبل التنفيذ.'));
    wrap.appendChild(list);

    if (check.allowed && App.can('CASE_TRANSFER')) {
      const b = el('button', 'btn btn-gold', 'تأكيد التحويل إلى ملف تنفيذ');
      b.onclick = async () => {
        if (!await App.confirm(
          'سيُفتح ملف تنفيذ جديد لهذه القضية وتُنقل إليه مرفقاتها. هل تريد المتابعة؟')) return;
        try {
          const r = await App.api.post(`/api/cases/${c.id}/transfer`);
          App.toast(r.message || 'تم التحويل', 'success');
          const id = r.data ? r.data.executionFileId : null;
          if (id) App.go('execution', { id }); else reload();
        } catch (e) { App.toastError(e); }
      };
      wrap.appendChild(el('div', 'mb-2'));
      wrap.appendChild(b);
    } else if (!check.allowed) {
      wrap.appendChild(el('div', 'note danger',
        'التحويل ممنوع حالياً — عالج الموانع المذكورة أعلاه أولاً.'));
    }

    box.appendChild(App.section('بوابة التحويل للتنفيذ — الشروط الستة', wrap));
    return box;
  }

  // ---------- سجل التواصل ----------
  function tabComms(d) {
    const box = el('div');
    const items = d.communications || [];
    if (!items.length) {
      box.appendChild(App.section('سجل التواصل', App.empty('لا يوجد سجل تواصل منقول', 'phone')));
      return box;
    }
    const list = el('ul', 'timeline');
    items.forEach(c => {
      const li = el('li', c.type === 'WARNING' ? 'danger' : '');
      li.innerHTML = `<div class="tl-title">${esc(App.labelOf('commType', c.type))}
          ${c.copiedFromId ? App.badge('منقول من الملف المالي', 'gold') : ''}</div>
        <div>${esc(c.summary || '')}</div>
        ${c.outcome ? `<div class="muted">النتيجة: ${esc(c.outcome)}</div>` : ''}
        <div class="tl-meta">${fmt.dateTime(c.commDate)}</div>`;
      list.appendChild(li);
    });
    box.appendChild(App.section('سجل التواصل والإنذارات', list));
    return box;
  }

  // ---------- المرفقات ----------
  function tabFiles(d, reload) {
    const items = (d.attachments || []).map(a => ({
      id: a.id, fileName: a.fileName, category: a.category, description: a.description,
      fileSize: a.fileSize, createdAt: a.createdAt, copiedFromId: a.copiedFromId
    }));
    const box = el('div');
    const actions = [];
    if (App.can('CASE_MANAGE')) {
      actions.push({ label: 'رفع مرفق', type: 'gold', onClick: () => uploadTo('CASE', d.case.id, reload) });
    }
    box.appendChild(App.section('المرفقات', App.table({
      columns: [
        { key: 'fileName', label: 'اسم الملف' },
        { key: 'category', label: 'الفئة', format: v => v ? App.badge(v, 'gold') : '—' },
        { key: 'copiedFromId', label: 'المصدر', format: v => v ? App.badge('منقول', 'info') : 'أُرفق هنا' },
        { key: 'createdAt', label: 'تاريخ الرفع', format: v => fmt.dateTime(v) },
        { key: 'id', label: '', noSort: true, format: (v, r) => {
            const b = el('button', 'btn btn-sm btn-ghost', 'تنزيل');
            b.onclick = e => { e.stopPropagation();
              App.api.download(`/api/attachments/${r.id}/download`, r.fileName); };
            return b;
          } }
      ],
      rows: items,
      empty: 'لا توجد مرفقات'
    }), actions));
    return box;
  }

  function uploadTo(entityType, entityId, reload) {
    const f = App.form([
      { key: 'file', label: 'الملف', type: 'file', required: true, col: 2 },
      { key: 'category', label: 'الفئة', type: 'text' },
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
            App.closeModal(); App.toast('تم رفع المرفق', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  // تُستخدم من صفحة التنفيذ أيضاً
  App.uploadTo = uploadTo;
})();
