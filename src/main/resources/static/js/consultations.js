/* =====================================================================
   المسار الثالث: الاستشارات القانونية.
   توزيع ذكي (٥٠/٣٠/٢٠)، مراجعة ثانية إلزامية، وقفل نهائي بعد التوقيع.
   ===================================================================== */

(function () {
  const { el, esc, fmt } = App;

  const STAGES = [
    ['RECEIVED', 'استلام'], ['STUDYING', 'دراسة'], ['UNDER_REVIEW', 'مراجعة'],
    ['APPROVED', 'اعتماد'], ['SIGNED', 'توقيع'], ['SENT', 'إرسال'], ['ARCHIVED', 'أرشفة']
  ];

  const tone = (s) => ({
    RECEIVED: 'info', STUDYING: 'info', UNDER_REVIEW: 'warn', RETURNED: 'danger',
    APPROVED: 'gold', SIGNED: 'ok', SENT: 'ok', ARCHIVED: 'muted'
  }[s] || 'muted');

  const prioTone = (p) => ({ URGENT: 'danger', HIGH: 'warn', NORMAL: 'info', LOW: 'muted' }[p] || 'muted');

  App.registerPage('consultations', {
    title: 'الاستشارات القانونية',
    icon: 'policy',
    permission: 'CONSULT_VIEW',
    render: async (root, params) => {
      if (params.id) await renderDetail(root, params.id);
      else await renderList(root);
    }
  });

  // ===================== القائمة =====================

  async function renderList(root) {
    const state = { status: '', q: '', page: 0, size: 100 };
    let view = 'board';

    const head = el('div', 'page-head');
    head.innerHTML = `<div><h2>الاستشارات القانونية</h2>
      <p class="page-sub">مسار مستقل بسبع مراحل ومراجعة مزدوجة قبل أي اعتماد</p></div>`;
    const btns = el('div', 'row');
    const toggle = el('button', 'btn btn-ghost', 'عرض جدول');
    btns.appendChild(toggle);
    if (App.can('CONSULT_MANAGE') || App.can('CONSULT_ASSIGN')) {
      const b = el('button', 'btn btn-gold', 'استشارة جديدة');
      b.onclick = () => openCreate();
      btns.appendChild(b);
    }
    head.appendChild(btns);
    root.appendChild(head);

    const filters = el('div', 'filters');
    const fq = el('label', 'fld grow');
    fq.innerHTML = '<span>بحث</span><input type="text" placeholder="رقم الاستشارة، الموضوع، الموكل">';
    const fs = el('label', 'fld');
    fs.innerHTML = '<span>المرحلة</span>';
    const sel = el('select');
    sel.appendChild(new Option('كل المراحل', ''));
    App.options('consultStatus').forEach(o => sel.appendChild(new Option(o.label, o.value)));
    fs.appendChild(sel);
    filters.appendChild(fq);
    filters.appendChild(fs);
    root.appendChild(filters);

    const box = el('div');
    root.appendChild(box);

    let timer = null;
    fq.querySelector('input').addEventListener('input', e => {
      clearTimeout(timer);
      timer = setTimeout(() => { state.q = e.target.value.trim(); load(); }, 320);
    });
    sel.onchange = () => { state.status = sel.value; load(); };
    toggle.onclick = () => {
      view = view === 'board' ? 'table' : 'board';
      toggle.textContent = view === 'board' ? 'عرض جدول' : 'عرض لوحة المراحل';
      load();
    };

    async function load() {
      const stop = App.spinner(box);
      try {
        const res = await App.api.get('/api/consultations', state);
        const rows = res.items || [];
        box.innerHTML = '';
        box.appendChild(view === 'board' ? board(rows) : tableView(rows));
      } catch (e) {
        box.innerHTML = '';
        box.appendChild(App.empty(e.message || 'تعذّر تحميل الاستشارات', 'warning'));
      } finally { stop(); }
    }
    await load();
  }

  /** لوحة المراحل السبع. */
  function board(rows) {
    const wrap = el('div');
    wrap.style.cssText = 'display:flex;gap:11px;overflow-x:auto;padding-bottom:10px';

    STAGES.forEach(([key, label]) => {
      const items = rows.filter(r => r.status === key
        || (key === 'STUDYING' && r.status === 'RETURNED'));
      const col = el('div');
      col.style.cssText = 'min-width:225px;flex:1';
      col.innerHTML = `<div style="font-weight:700;color:var(--navy);padding:7px 4px;
        border-bottom:3px solid var(--gold);margin-bottom:9px">
        ${esc(label)} <span class="badge muted">${items.length}</span></div>`;

      if (!items.length) {
        col.appendChild(el('div', 'muted', 'لا يوجد'));
      }
      items.forEach(r => {
        const c = el('div', 'card');
        c.style.cssText = 'margin-bottom:9px;cursor:pointer';
        const body = el('div', 'card-body');
        body.style.padding = '11px';
        body.innerHTML = `<div class="row between mb-1">
            <strong style="font-size:12.5px">${esc(r.consultationNumber)}</strong>
            ${App.badge(r.priorityLabel, prioTone(r.priority))}</div>
          <div style="font-weight:600;font-size:13px">${esc(r.subject)}</div>
          <div class="muted" style="font-size:12px">${esc(r.client ? r.client.name : '—')}</div>
          <div class="muted" style="font-size:12px">${esc(r.consultant ? r.consultant.fullName : 'غير مسند')}</div>
          ${r.dueDate ? `<div style="font-size:11.5px;margin-top:4px" class="${r.overdue ? '' : 'muted'}">
            ${r.overdue ? App.badge('متأخرة', 'danger') : 'يستحق ' + fmt.date(r.dueDate)}</div>` : ''}
          ${r.locked ? `<div style="font-size:11.5px;margin-top:4px;display:flex;align-items:center;gap:4px">${App.icon('lock', 13)} موقّع ومقفل</div>` : ''}
          ${r.status === 'RETURNED' ? '<div style="font-size:11.5px;margin-top:4px">'
            + App.badge('معادة بملاحظات', 'danger') + '</div>' : ''}`;
        c.appendChild(body);
        c.onclick = () => App.go('consultations', { id: r.id });
        col.appendChild(c);
      });
      wrap.appendChild(col);
    });
    return wrap;
  }

  function tableView(rows) {
    return App.table({
      columns: [
        { key: 'consultationNumber', label: 'الرقم' },
        { key: 'subject', label: 'الموضوع' },
        { key: 'client', label: 'الموكل', format: v => v ? esc(v.name) : '—' },
        { key: 'specialization', label: 'التخصص' },
        { key: 'consultant', label: 'المستشار', format: v => v ? esc(v.fullName) : '—' },
        { key: 'statusLabel', label: 'المرحلة', align: 'center',
          format: (v, r) => App.badge(v, tone(r.status)) },
        { key: 'priorityLabel', label: 'الأولوية', align: 'center',
          format: (v, r) => App.badge(v, prioTone(r.priority)) },
        { key: 'dueDate', label: 'الاستحقاق',
          format: (v, r) => v ? (r.overdue ? `${fmt.date(v)} ${App.badge('متأخرة', 'danger')}` : fmt.date(v)) : '—' },
        { key: 'locked', label: 'مقفل', align: 'center', noSort: true, format: v => v ? App.icon('lock', 15) : '' }
      ],
      rows,
      onRow: r => App.go('consultations', { id: r.id }),
      empty: 'لا توجد استشارات مطابقة'
    });
  }

  // ===================== إنشاء =====================

  function openCreate() {
    const specs = (App.state.lookups.specializations || []).map(s => ({ value: s, label: s }));
    const f = App.form([
      { key: 'clientId', label: 'الموكل', type: 'select', options: App.people('clients') },
      { key: 'specialization', label: 'التخصص المطلوب', type: 'select', options: specs,
        onChange: () => refreshCandidates() },
      { key: 'subject', label: 'الموضوع', type: 'text', required: true, col: 2 },
      { key: 'requestText', label: 'نص الطلب', type: 'textarea' },
      { key: 'priority', label: 'الأولوية', type: 'select', options: App.options('priority') },
      { key: 'dueDate', label: 'تاريخ الاستحقاق', type: 'date' },
      { key: 'consultantId', label: 'المستشار (اتركه فارغاً للتوزيع الذكي)', type: 'select',
        options: App.people('consultants'), col: 2 }
    ], { priority: 'NORMAL' });

    const candBox = el('div');

    async function refreshCandidates() {
      const spec = f.inputs.specialization.value;
      candBox.innerHTML = '';
      if (!spec) return;
      try {
        const list = await App.api.get('/api/consultations/suggest', { specialization: spec });
        candBox.appendChild(candidateTable(list, (id) => { f.inputs.consultantId.value = id; }));
      } catch (e) { /* تجاهل */ }
    }

    const body = el('div');
    body.appendChild(f.node);
    body.appendChild(el('div', 'mb-2'));
    body.appendChild(candBox);

    App.modal({
      title: 'استشارة جديدة', width: 'wide', bodyNode: body,
      actions: [
        { label: 'تسجيل الاستشارة', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = await App.api.post('/api/consultations', v);
            App.closeModal();
            App.toast(r.message || 'تم التسجيل', 'success');
            const id = r.data && r.data.consultation ? r.data.consultation.id : null;
            if (id) App.go('consultations', { id }); else App.route();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  /** جدول ترتيب المرشحين بالدرجات — يشرح معادلة التوزيع الذكي. */
  function candidateTable(list, onPick) {
    const box = el('div');
    if (!list || !list.length) {
      box.appendChild(el('div', 'note warn', 'لا يوجد مستشارون متاحون حالياً.'));
      return box;
    }
    const rows = list.map((c, i) => Object.assign({}, c, { rank: i + 1 }));
    box.appendChild(el('div', 'form-hint', 'ترتيب المرشحين وفق التوزيع الذكي:'));
    box.appendChild(App.table({
      columns: [
        { key: 'rank', label: '#', align: 'center', noSort: true,
          format: (v) => v === 1 ? App.badge('الأعلى', 'gold') : v },
        { key: 'fullName', label: 'المستشار', noSort: true },
        { key: 'specializationScore', label: 'التخصص (٥٠)', align: 'num', noSort: true,
          format: v => v.toFixed(1) },
        { key: 'loadScore', label: 'انخفاض الحمل (٣٠)', align: 'num', noSort: true,
          format: v => v.toFixed(1) },
        { key: 'seniorityScore', label: 'الأقدمية (٢٠)', align: 'num', noSort: true,
          format: v => v.toFixed(1) },
        { key: 'score', label: 'المجموع', align: 'num', noSort: true,
          format: v => `<strong>${v.toFixed(1)}</strong>` },
        { key: 'activeCount', label: 'استشارات نشطة', align: 'num', noSort: true },
        { key: 'years', label: 'سنوات الخدمة', align: 'num', noSort: true, format: v => v.toFixed(1) },
        { key: 'userId', label: '', noSort: true, format: (v) => {
            if (!onPick) return '';
            const b = el('button', 'btn btn-sm btn-ghost', 'إسناد يدوي');
            b.onclick = (e) => { e.stopPropagation(); onPick(v); App.toast('تم اختياره يدوياً'); };
            return b;
          } }
      ],
      rows
    }));
    box.appendChild(el('div', 'note',
      'معادلة التوزيع الذكي: مطابقة التخصص ٥٠ + انخفاض الحمل ٣٠ + الخبرة والأقدمية ٢٠. '
      + 'وعند التعادل يُقدَّم الأقل حملاً، وغياب المتخصص لا يوقف التوزيع، '
      + 'والتوزيع اليدوي متاح دائماً ويتجاوز الترتيب كلياً.'));
    return box;
  }

  // ===================== التفاصيل =====================

  async function renderDetail(root, id) {
    const d = await App.api.get('/api/consultations/' + id);
    const c = d.consultation;
    const reload = () => App.route();
    const me = App.state.user;
    const isConsultant = c.consultant && me && c.consultant.id === me.id;

    const head = el('div', 'page-head');
    head.innerHTML = `<div>
      <h2>الاستشارة ${esc(c.consultationNumber)} ${App.badge(c.statusLabel, tone(c.status))}</h2>
      <p class="page-sub">${esc(c.subject)}${c.client ? ' — ' + esc(c.client.name) : ''}</p></div>`;
    const back = el('button', 'btn btn-ghost', '← رجوع للقائمة');
    back.onclick = () => App.go('consultations');
    head.appendChild(back);
    root.appendChild(head);

    if (c.supersedesId) {
      const n = el('div', 'note');
      n.innerHTML = `هذه الاستشارة رأي تصحيحي لرأي موقّع سابق.
        <button class="link-btn" id="go-orig">فتح الرأي الأصلي</button>`;
      root.appendChild(n);
      n.querySelector('#go-orig').onclick = () => App.go('consultations', { id: c.supersedesId });
    }

    if (c.locked) {
      root.appendChild(el('div', 'lock-bar',
        `<span class="lock-icon">${App.icon('lock', 18)}</span><span>رأي موقّع ومقفل — لا يُعدَّل ولو من المدير،
         وأي تصحيح يكون برأي جديد</span>`));
    }

    // شريط المراحل
    const stepper = el('div', 'stepper');
    const curIdx = STAGES.findIndex(([k]) => k === c.status);
    STAGES.forEach(([key, label], i) => {
      let cls = 'step';
      if (c.status === 'RETURNED' && key === 'STUDYING') cls += ' current';
      else if (curIdx >= 0 && i < curIdx) cls += ' done';
      else if (i === curIdx) cls += ' current';
      const s = el('div', cls);
      s.innerHTML = (curIdx >= 0 && i < curIdx ? '✓ ' : '') + esc(label);
      stepper.appendChild(s);
    });
    root.appendChild(stepper);

    if (c.status === 'RETURNED') {
      root.appendChild(el('div', 'note danger',
        'أُعيدت الاستشارة إلى المستشار مع ملاحظات المراجعة'
        + (c.reviewNotes ? ': ' + c.reviewNotes : '.')));
    }

    // بطاقة الإسناد
    const assign = el('div');
    assign.appendChild(kv([
      ['المستشار المسند', c.consultant ? c.consultant.fullName : 'غير مسند'],
      ['سبب الإسناد', c.assignmentReason],
      ['الدرجة المحسوبة', c.assignmentScore !== null && c.assignmentScore !== undefined
        ? c.assignmentScore.toFixed(1) + ' من ١٠٠' : null],
      ['طريقة الإسناد', c.assignedManually ? 'يدوي' : (c.consultant ? 'تلقائي بالتوزيع الذكي' : null)],
      ['التخصص المطلوب', c.specialization],
      ['الأولوية', c.priorityLabel],
      ['تاريخ الاستلام', fmt.date(c.receivedAt)],
      ['تاريخ الاستحقاق', c.dueDate ? fmt.date(c.dueDate) + (c.overdue ? ' (متأخرة)' : '') : null],
      ['المراجع', c.reviewer ? c.reviewer.fullName : null],
      ['اعتمده', c.approvedBy ? c.approvedBy.fullName : null],
      ['وقّعه', c.signedBy ? c.signedBy.fullName + ' — ' + fmt.dateTime(c.signedAt) : null],
      ['أُرسل إلى', c.sentTo ? c.sentTo + ' — ' + fmt.dateTime(c.sentAt) : null]
    ]));
    const assignActions = [];
    if (!c.locked && App.can('CONSULT_ASSIGN')) {
      assignActions.push({ label: 'إسناد / إعادة إسناد', type: 'gold',
        onClick: () => openAssign(c, reload) });
    }
    root.appendChild(App.section('الإسناد', assign, assignActions));

    // نص الطلب
    if (c.requestText) {
      const p = el('div');
      p.style.whiteSpace = 'pre-wrap';
      p.textContent = c.requestText;
      root.appendChild(App.section('نص الطلب', p));
    }

    // الرأي القانوني
    root.appendChild(opinionSection(c, isConsultant, reload));

    // المراجعة
    if (!c.locked && c.status === 'UNDER_REVIEW') {
      root.appendChild(reviewSection(c, isConsultant, reload));
    }

    // التوقيع
    if (!c.locked && c.status === 'APPROVED' && App.can('CONSULT_SIGN')) {
      const box = el('div');
      box.appendChild(el('div', 'note warn',
        'بعد التوقيع يُقفل الرأي نهائياً ولا يُعدَّل ولو من المدير. أي تصحيح يكون برأي جديد.'));
      const b = el('button', 'btn btn-gold', 'التوقيع النهائي');
      b.onclick = async () => {
        if (!await App.confirm(
          'بعد التوقيع يُقفل الرأي نهائياً ولا يُعدَّل ولو من المدير. هل تريد المتابعة؟')) return;
        try {
          const r = await App.api.post(`/api/consultations/${c.id}/sign`);
          App.toast(r.message || 'تم التوقيع', 'success');
          reload();
        } catch (e) { App.toastError(e); }
      };
      box.appendChild(b);
      root.appendChild(App.section('التوقيع النهائي', box));
    }

    // بعد التوقيع: إرسال + تصحيح
    if (c.locked) {
      const box = el('div', 'row');
      if (c.status === 'SIGNED') {
        const s = el('button', 'btn btn-gold', 'إرسال الرأي');
        s.onclick = () => {
          const f = App.form([{ key: 'sentTo', label: 'الجهة المرسل إليها',
            type: 'text', required: true, col: 2 }],
            { sentTo: c.client ? c.client.name : '' });
          App.modal({
            title: 'إرسال الرأي', bodyNode: f.node,
            actions: [
              { label: 'إرسال', type: 'gold', onClick: async () => {
                  const r = await App.api.post(`/api/consultations/${c.id}/send`, f.read());
                  App.closeModal(); App.toast(r.message || 'تم الإرسال', 'success'); reload();
                } },
              { label: 'إلغاء', onClick: () => App.closeModal() }
            ]
          });
        };
        box.appendChild(s);
      }
      if (c.status === 'SENT' || c.status === 'SIGNED') {
        const a = el('button', 'btn btn-ghost', 'أرشفة');
        a.onclick = async () => {
          if (!await App.confirm('أرشفة الاستشارة؟')) return;
          try {
            const r = await App.api.post(`/api/consultations/${c.id}/archive`);
            App.toast(r.message || 'تمت الأرشفة', 'success'); reload();
          } catch (e) { App.toastError(e); }
        };
        box.appendChild(a);
      }
      const corr = el('button', 'btn btn-ghost', 'إنشاء رأي تصحيحي');
      corr.onclick = async () => {
        if (!await App.confirm(
          'سيُنشأ رأي جديد يشير إلى هذا الرأي الموقّع. الرأي الأصلي يبقى كما هو دون تعديل. متابعة؟')) return;
        try {
          const r = await App.api.post(`/api/consultations/${c.id}/correction`);
          App.toast(r.message || 'أُنشئ الرأي التصحيحي', 'success');
          const nid = r.data ? r.data.id : null;
          if (nid) App.go('consultations', { id: nid }); else reload();
        } catch (e) { App.toastError(e); }
      };
      box.appendChild(corr);

      const pr = el('button', 'btn btn-ghost', 'طباعة الرأي');
      pr.onclick = () => printOpinion(c);
      box.appendChild(pr);

      root.appendChild(App.section('الإجراءات المتاحة بعد التوقيع', box));
    }

    // سجل المراحل
    root.appendChild(actionsSection(d.actions || []));

    // المرفقات
    root.appendChild(attachments(d, c, reload));
  }

  function kv(rows) {
    const box = el('div');
    rows.forEach(([k, v]) => {
      if (v === null || v === undefined || v === '') return;
      const r = el('div');
      r.style.cssText = 'display:flex;gap:10px;padding:7px 0;border-bottom:1px dotted #eee';
      r.innerHTML = `<b style="min-width:180px;color:var(--navy)">${esc(k)}</b><span>${esc(v)}</span>`;
      box.appendChild(r);
    });
    return box;
  }

  function openAssign(c, reload) {
    const box = el('div');
    const f = App.form([{ key: 'consultantId', label: 'المستشار (اتركه فارغاً للتوزيع التلقائي)',
      type: 'select', options: App.people('consultants'), col: 2 }],
      { consultantId: c.consultant ? c.consultant.id : '' });
    box.appendChild(f.node);
    const cand = el('div');
    box.appendChild(cand);

    App.api.get('/api/consultations/suggest', { specialization: c.specialization })
      .then(list => cand.appendChild(candidateTable(list, id => { f.inputs.consultantId.value = id; })))
      .catch(() => {});

    App.modal({
      title: 'إسناد الاستشارة', width: 'wide', bodyNode: box,
      actions: [
        { label: 'حفظ الإسناد', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = await App.api.post(`/api/consultations/${c.id}/assign`,
              { consultantId: v.consultantId || null });
            App.closeModal(); App.toast(r.message || 'تم الإسناد', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  function opinionSection(c, isConsultant, reload) {
    const editable = !c.locked && isConsultant
      && (c.status === 'STUDYING' || c.status === 'RETURNED' || c.status === 'RECEIVED');

    if (!editable) {
      const p = el('div');
      if (c.opinionText) {
        p.style.cssText = 'white-space:pre-wrap;line-height:2';
        p.textContent = c.opinionText;
      } else {
        return App.section('الرأي القانوني', App.empty('لم يُكتب الرأي بعد', 'edit'));
      }
      return App.section('الرأي القانوني', p);
    }

    const box = el('div');
    const ta = el('textarea');
    ta.value = c.opinionText || '';
    ta.rows = 12;
    ta.style.width = '100%';
    box.appendChild(ta);

    const row = el('div', 'row');
    row.style.marginTop = '10px';
    const save = el('button', 'btn btn-ghost', 'حفظ المسودة');
    save.onclick = async () => {
      try {
        const r = await App.api.post(`/api/consultations/${c.id}/opinion`, { opinionText: ta.value });
        App.toast(r.message || 'تم الحفظ', 'success');
      } catch (e) { App.toastError(e); }
    };
    const submit = el('button', 'btn btn-gold', 'إرسال للمراجعة');
    submit.onclick = async () => {
      try {
        await App.api.post(`/api/consultations/${c.id}/opinion`, { opinionText: ta.value });
        const r = await App.api.post(`/api/consultations/${c.id}/submit-review`);
        App.toast(r.message || 'أُرسل للمراجعة', 'success');
        reload();
      } catch (e) { App.toastError(e); }
    };
    row.appendChild(save);
    row.appendChild(submit);
    box.appendChild(row);
    box.appendChild(el('div', 'note',
      'المراجعة الثانية إلزامية: لا يراجع كاتب الرأي رأيه، والمراجع يجب أن يملك صلاحية المراجعة.'));
    return App.section('كتابة الرأي القانوني', box);
  }

  function reviewSection(c, isConsultant, reload) {
    if (isConsultant) {
      return App.section('المراجعة', el('div', 'note danger',
        'لا يمكنك مراجعة رأيك — المراجعة الثانية إلزامية من مستشار آخر.'));
    }
    if (!App.can('CONSULT_REVIEW')) {
      return App.section('المراجعة', el('div', 'note',
        'الرأي بانتظار المراجعة من مستشار يملك صلاحية المراجعة.'));
    }

    const box = el('div');
    const notes = el('textarea');
    notes.rows = 4;
    notes.placeholder = 'ملاحظات المراجعة (إلزامية عند الإعادة)';
    notes.style.width = '100%';
    box.appendChild(notes);

    const row = el('div', 'row');
    row.style.marginTop = '10px';
    const ok = el('button', 'btn btn-ok', 'اعتماد الرأي');
    ok.onclick = async () => {
      try {
        const r = await App.api.post(`/api/consultations/${c.id}/review`,
          { approved: true, notes: notes.value });
        App.toast(r.message || 'تم الاعتماد', 'success'); reload();
      } catch (e) { App.toastError(e); }
    };
    const back = el('button', 'btn btn-danger', 'إعادة مع ملاحظات');
    back.onclick = async () => {
      if (!notes.value.trim()) { App.toast('ملاحظات الإعادة إلزامية', 'error'); return; }
      try {
        const r = await App.api.post(`/api/consultations/${c.id}/review`,
          { approved: false, notes: notes.value });
        App.toast(r.message || 'أُعيد للمستشار', 'success'); reload();
      } catch (e) { App.toastError(e); }
    };
    row.appendChild(ok);
    row.appendChild(back);
    box.appendChild(row);
    return App.section('المراجعة الثانية', box);
  }

  function actionsSection(actions) {
    if (!actions.length) return App.section('سجل المراحل', App.empty('لا توجد مراحل مسجّلة', 'schedule'));
    const list = el('ul', 'timeline');
    actions.forEach(a => {
      const li = el('li', a.action === 'RETURN' ? 'danger' : a.action === 'SIGN' ? 'done' : '');
      li.innerHTML = `<div class="tl-title">${esc(a.actionLabel || a.action)}</div>
        ${a.notes ? `<div>${esc(a.notes)}</div>` : ''}
        <div class="tl-meta">${a.actor ? esc(a.actor.fullName) + ' — ' : ''}${fmt.dateTime(a.actedAt)}
        ${a.toStatusLabel ? ' — إلى مرحلة: ' + esc(a.toStatusLabel) : ''}</div>`;
      list.appendChild(li);
    });
    return App.section('سجل المراحل', list);
  }

  function attachments(d, c, reload) {
    const actions = [];
    if (!c.locked && App.can('CONSULT_MANAGE')) {
      actions.push({ label: 'رفع مرفق', type: 'gold',
        onClick: () => App.uploadTo('CONSULTATION', c.id, reload) });
    }
    return App.section('المرفقات', App.table({
      columns: [
        { key: 'fileName', label: 'اسم الملف' },
        { key: 'category', label: 'الفئة', format: v => v ? App.badge(v, 'gold') : '—' },
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
    }), actions);
  }

  function printOpinion(c) {
    App.printHtml('رأي قانوني رقم ' + c.consultationNumber, `
      <div class="kv"><b>رقم الرأي</b><span>${esc(c.consultationNumber)}</span></div>
      <div class="kv"><b>الموضوع</b><span>${esc(c.subject)}</span></div>
      <div class="kv"><b>الموكل</b><span>${esc(c.client ? c.client.name : '—')}</span></div>
      <div class="kv"><b>التخصص</b><span>${esc(c.specialization || '—')}</span></div>
      <div class="kv"><b>تاريخ الاستلام</b><span>${fmt.date(c.receivedAt)}</span></div>
      ${c.requestText ? `<h3 style="margin-top:20px;color:#0f2c4c">نص الطلب</h3>
        <p style="white-space:pre-wrap">${esc(c.requestText)}</p>` : ''}
      <h3 style="margin-top:20px;color:#0f2c4c">الرأي القانوني</h3>
      <p style="white-space:pre-wrap;line-height:2">${esc(c.opinionText || '')}</p>
      <div class="sign-row">
        <div class="sign-box"><div class="sign-line">${esc(c.consultant ? c.consultant.fullName : 'المستشار')}<br>المستشار القانوني</div></div>
        <div class="sign-box"><div class="sign-line">${esc(c.signedBy ? c.signedBy.fullName : '')}<br>كبير المستشارين — ${fmt.date(c.signedAt)}</div></div>
      </div>`);
  }
})();
