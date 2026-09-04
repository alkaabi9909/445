/* =====================================================================
   شاشات الإدارة: المستخدمون والأدوار، الإعدادات، سجل النشاطات، التقارير.
   ===================================================================== */

(function () {
  const { el, esc, fmt } = App;

  // ===================== المستخدمون والأدوار =====================

  App.registerPage('users', {
    title: 'المستخدمون والأدوار',
    icon: 'group',
    permission: 'USERS_MANAGE',
    render: async (root) => {
      root.appendChild(el('div', 'page-head',
        `<div><h2>المستخدمون والأدوار</h2>
         <p class="page-sub">أربعة أدوار أساسية، مع إمكانية إنشاء أدوار مخصصة بصلاحيات دقيقة</p></div>`));

      const usersBox = el('div');
      const rolesBox = el('div');
      root.appendChild(usersBox);
      root.appendChild(rolesBox);

      await loadUsers(usersBox);
      await loadRoles(rolesBox);
    }
  });

  async function loadUsers(box) {
    const stop = App.spinner(box);
    try {
      const [users, roles] = await Promise.all([
        App.api.get('/api/users'), App.api.get('/api/roles')
      ]);
      box.innerHTML = '';
      const reload = () => loadUsers(box);

      box.appendChild(App.section('المستخدمون', App.table({
        columns: [
          { key: 'fullName', label: 'الاسم' },
          { key: 'username', label: 'اسم المستخدم' },
          { key: 'role', label: 'الدور', format: v => v ? esc(v.nameAr) : '—' },
          { key: 'specialization', label: 'التخصص' },
          { key: 'joinedAt', label: 'تاريخ الالتحاق', format: v => fmt.date(v) },
          { key: 'lastLoginAt', label: 'آخر دخول', format: v => fmt.dateTime(v) },
          { key: 'active', label: 'الحالة', align: 'center', format: (v, r) => {
              if (!v) return App.badge('موقوف', 'muted');
              if (r.lockedUntil && new Date(r.lockedUntil) > new Date()) {
                return App.badge('مقفل حتى ' + fmt.dateTime(r.lockedUntil), 'danger');
              }
              return App.badge('نشط', 'ok');
            } },
          { key: 'id', label: 'إجراءات', noSort: true, format: (v, r) => {
              const w = el('div', 'row');
              w.style.gap = '5px';
              const ed = el('button', 'btn btn-sm btn-ghost', 'تعديل');
              ed.onclick = () => openUser(r, roles, reload);
              w.appendChild(ed);
              const rp = el('button', 'btn btn-sm btn-ghost', 'كلمة مرور');
              rp.onclick = () => resetPassword(r, reload);
              w.appendChild(rp);
              if (r.lockedUntil && new Date(r.lockedUntil) > new Date()) {
                const un = el('button', 'btn btn-sm btn-ok', 'فك القفل');
                un.onclick = async () => {
                  try {
                    const res = await App.api.post(`/api/users/${r.id}/unlock`);
                    App.toast(res.message || 'تم فك القفل', 'success'); reload();
                  } catch (e) { App.toastError(e); }
                };
                w.appendChild(un);
              }
              return w;
            } }
        ],
        rows: users,
        empty: 'لا يوجد مستخدمون'
      }), [{ label: 'مستخدم جديد', type: 'gold', onClick: () => openUser(null, roles, reload) }]));
    } catch (e) {
      box.innerHTML = '';
      box.appendChild(App.empty(e.message || 'تعذّر تحميل المستخدمين', 'warning'));
    } finally { stop(); }
  }

  function openUser(u, roles, reload) {
    const fields = [
      { key: 'fullName', label: 'الاسم الكامل', type: 'text', required: true },
      { key: 'username', label: 'اسم المستخدم', type: 'text', required: true, disabled: !!u },
      { key: 'roleId', label: 'الدور', type: 'select', required: true,
        options: roles.map(r => ({ value: r.id, label: r.nameAr })) },
      { key: 'specialization', label: 'التخصص', type: 'text' },
      { key: 'email', label: 'البريد الإلكتروني', type: 'text' },
      { key: 'phone', label: 'الهاتف', type: 'text' },
      { key: 'joinedAt', label: 'تاريخ الالتحاق', type: 'date',
        hint: 'يُستخدم في حساب الأقدمية بالتوزيع الذكي' },
      { key: 'active', label: 'نشط', type: 'checkbox' }
    ];
    if (!u) {
      fields.push({ key: 'password', label: 'كلمة المرور المبدئية', type: 'text', required: true, col: 2,
        hint: 'ثمانية أحرف على الأقل، وتشمل حرفاً كبيراً وصغيراً ورقماً ورمزاً' });
    }
    const f = App.form(fields, u ? {
      fullName: u.fullName, username: u.username,
      roleId: u.role ? u.role.id : '', specialization: u.specialization,
      email: u.email, phone: u.phone, joinedAt: u.joinedAt, active: u.active
    } : { active: true });

    App.modal({
      title: u ? 'تعديل مستخدم' : 'مستخدم جديد', width: 'wide', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = u ? await App.api.put('/api/users/' + u.id, v)
                        : await App.api.post('/api/users', v);
            App.closeModal(); App.toast(r.message || 'تم الحفظ', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  function resetPassword(u, reload) {
    const f = App.form([{ key: 'password', label: 'كلمة المرور الجديدة',
      type: 'text', required: true, col: 2,
      hint: 'ثمانية أحرف على الأقل، وتشمل حرفاً كبيراً وصغيراً ورقماً ورمزاً' }], {});
    App.modal({
      title: 'إعادة تعيين كلمة مرور: ' + u.fullName, bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const r = await App.api.post(`/api/users/${u.id}/reset-password`, f.read());
            App.closeModal(); App.toast(r.message || 'تم التعيين', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  async function loadRoles(box) {
    try {
      const [roles, perms] = await Promise.all([
        App.api.get('/api/roles'), App.api.get('/api/roles/permissions')
      ]);
      box.innerHTML = '';
      const reload = () => loadRoles(box);

      const grid = el('div', 'grid-3');
      roles.forEach(r => {
        const c = el('div', 'card');
        const b = el('div', 'card-body');
        b.innerHTML = `<div class="row between mb-1">
            <strong>${esc(r.nameAr)}</strong>
            ${r.system ? App.badge('دور أساسي', 'gold') : App.badge('مخصص', 'info')}</div>
          <div class="muted" style="font-size:12.5px">${esc(r.description || '')}</div>
          <div style="margin-top:8px">${App.badge(r.permissions.length + ' صلاحية', 'muted')}</div>`;
        const row = el('div', 'row');
        row.style.marginTop = '9px';
        const view = el('button', 'btn btn-sm btn-ghost', r.system ? 'عرض الصلاحيات' : 'تعديل');
        view.onclick = () => openRole(r, perms, reload);
        row.appendChild(view);
        if (!r.system) {
          const del = el('button', 'btn btn-sm btn-danger', 'حذف');
          del.onclick = async () => {
            if (!await App.confirm('حذف الدور «' + r.nameAr + '»؟')) return;
            try { await App.api.del('/api/roles/' + r.id); App.toast('تم الحذف', 'success'); reload(); }
            catch (e) { App.toastError(e); }
          };
          row.appendChild(del);
        }
        b.appendChild(row);
        c.appendChild(b);
        grid.appendChild(c);
      });

      const wrap = el('div');
      wrap.appendChild(el('div', 'note',
        'الأدوار الأربعة الأساسية للقراءة فقط. يمكنك إنشاء أدوار مخصصة بصلاحيات دقيقة '
        + '— مثل سكرتير أو موظف إدخال بيانات.'));
      wrap.appendChild(grid);

      box.appendChild(App.section('الأدوار والصلاحيات', wrap,
        [{ label: 'دور مخصص جديد', type: 'gold', onClick: () => openRole(null, perms, reload) }]));
    } catch (e) {
      box.innerHTML = '';
      box.appendChild(App.empty(e.message || 'تعذّر تحميل الأدوار', 'warning'));
    }
  }

  function openRole(r, perms, reload) {
    const readOnly = r && r.system;
    const body = el('div');
    if (readOnly) {
      body.appendChild(el('div', 'note warn',
        'هذا دور أساسي — صلاحياته ثابتة ولا تقبل التعديل أو الحذف.'));
    }
    const f = App.form([
      { key: 'code', label: 'رمز الدور', type: 'text', required: !r, disabled: !!r },
      { key: 'nameAr', label: 'اسم الدور بالعربية', type: 'text', required: true, disabled: readOnly },
      { key: 'description', label: 'الوصف', type: 'text', col: 2, disabled: readOnly }
    ], r || {});
    body.appendChild(f.node);

    // شبكة الصلاحيات
    const chosen = new Set(r ? r.permissions : []);
    const grid = el('div');
    grid.style.cssText = 'display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:6px;margin-top:14px';
    perms.forEach(p => {
      const lab = el('label');
      lab.style.cssText = 'display:flex;gap:8px;align-items:center;padding:6px 9px;border:1px solid var(--border);border-radius:8px';
      const cb = el('input');
      cb.type = 'checkbox';
      cb.checked = chosen.has(p.code);
      cb.disabled = readOnly;
      cb.style.width = 'auto';
      cb.onchange = () => { if (cb.checked) chosen.add(p.code); else chosen.delete(p.code); };
      lab.appendChild(cb);
      lab.appendChild(el('span', null, esc(p.label)));
      grid.appendChild(lab);
    });
    body.appendChild(el('div', 'form-hint', 'الصلاحيات:'));
    body.appendChild(grid);

    const actions = readOnly
      ? [{ label: 'إغلاق', onClick: () => App.closeModal() }]
      : [
          { label: 'حفظ', type: 'gold', onClick: async () => {
              const v = f.read();
              v.permissions = Array.from(chosen);
              const res = r ? await App.api.put('/api/roles/' + r.id, v)
                            : await App.api.post('/api/roles', v);
              App.closeModal(); App.toast(res.message || 'تم الحفظ', 'success'); reload();
            } },
          { label: 'إلغاء', onClick: () => App.closeModal() }
        ];

    App.modal({ title: r ? 'الدور: ' + r.nameAr : 'دور مخصص جديد',
      width: 'wide', bodyNode: body, actions });
  }

  // ===================== الإعدادات =====================

  App.registerPage('settings', {
    title: 'الإعدادات',
    icon: 'settings',
    permission: 'SETTINGS_MANAGE',
    render: async (root) => {
      root.appendChild(el('div', 'page-head',
        `<div><h2>الإعدادات</h2><p class="page-sub">ضبط قواعد العمل العامة للنظام</p></div>`));

      const list = await App.api.get('/api/settings');
      const groups = {};
      list.forEach(s => {
        const g = s.settingGroup || 'عام';
        (groups[g] = groups[g] || []).push(s);
      });

      const inputs = {};
      Object.entries(groups).forEach(([g, items]) => {
        const body = el('div', 'form-grid');
        items.forEach(s => {
          const w = el('label', 'fld col-2');
          w.innerHTML = `<span>${esc(s.nameAr)}</span>`;
          let inp;
          if (s.valueType === 'BOOLEAN') {
            inp = el('input'); inp.type = 'checkbox';
            inp.checked = s.settingValue === 'true';
            inp.style.width = 'auto';
          } else {
            inp = el('input');
            inp.type = s.valueType === 'NUMBER' ? 'number' : 'text';
            inp.value = s.settingValue || '';
          }
          w.appendChild(inp);
          if (s.description) w.appendChild(el('div', 'form-hint', esc(s.description)));
          inputs[s.settingKey] = { input: inp, type: s.valueType };
          body.appendChild(w);
        });
        root.appendChild(App.section(g, body));
      });

      const save = el('button', 'btn btn-gold', 'حفظ الإعدادات');
      save.onclick = async () => {
        const payload = {};
        Object.entries(inputs).forEach(([k, v]) => {
          payload[k] = v.type === 'BOOLEAN' ? String(v.input.checked) : v.input.value;
        });
        try {
          const r = await App.api.put('/api/settings', payload);
          App.toast(r.message || 'تم الحفظ', 'success');
        } catch (e) { App.toastError(e); }
      };
      root.appendChild(save);
    }
  });

  // ===================== سجل النشاطات =====================

  App.registerPage('audit', {
    title: 'سجل النشاطات',
    icon: 'receipt_long',
    permission: 'AUDIT_VIEW',
    render: async (root) => {
      const state = { username: '', action: '', entityType: '', from: '', to: '', page: 0, size: 25 };

      const head = el('div', 'page-head');
      head.innerHTML = `<div><h2>سجل النشاطات</h2>
        <p class="page-sub">كل إنشاء أو تعديل أو حذف، وكل محاولة دخول — بالوقت والجهاز</p></div>`;
      const exp = el('button', 'btn btn-ghost', 'تصدير Excel');
      exp.onclick = () => {
        const p = new URLSearchParams();
        ['username', 'action', 'entityType', 'from', 'to'].forEach(k => {
          if (state[k]) p.append(k, state[k]);
        });
        App.api.download('/api/audit/export?' + p.toString(), 'سجل-النشاطات.xlsx');
      };
      head.appendChild(exp);
      root.appendChild(head);

      const filters = el('div', 'filters');
      const mk = (label, type, key) => {
        const w = el('label', 'fld');
        w.innerHTML = `<span>${label}</span>`;
        const i = el('input');
        i.type = type;
        i.oninput = () => { state[key] = i.value.trim(); state.page = 0; load(); };
        w.appendChild(i);
        filters.appendChild(w);
      };
      mk('المستخدم', 'text', 'username');
      mk('نوع الإجراء', 'text', 'action');
      mk('نوع الكيان', 'text', 'entityType');
      mk('من تاريخ', 'date', 'from');
      mk('إلى تاريخ', 'date', 'to');
      root.appendChild(filters);

      const box = el('div');
      root.appendChild(box);

      if (App.can('BACKUP_RUN')) root.appendChild(await backupCard());

      async function load() {
        const stop = App.spinner(box);
        try {
          const res = await App.api.get('/api/audit', state);
          box.innerHTML = '';
          box.appendChild(App.table({
            columns: [
              { key: 'actedAt', label: 'الوقت', format: v => fmt.dateTime(v) },
              { key: 'fullName', label: 'الاسم' },
              { key: 'username', label: 'المستخدم' },
              { key: 'action', label: 'الإجراء' },
              { key: 'entityType', label: 'الكيان' },
              { key: 'entityRef', label: 'المرجع' },
              { key: 'details', label: 'التفاصيل' },
              { key: 'ipAddress', label: 'الجهاز' },
              { key: 'success', label: 'النتيجة', align: 'center',
                format: v => v ? App.badge('ناجح', 'ok') : App.badge('فاشل', 'danger') }
            ],
            rows: res.items || [],
            empty: 'لا توجد سجلات مطابقة'
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
          } else { p.innerHTML = `<span class="muted">${total} سجل</span>`; }
          box.appendChild(p);
        } catch (e) {
          box.innerHTML = '';
          box.appendChild(App.empty(e.message || 'تعذّر تحميل السجل', 'warning'));
        } finally { stop(); }
      }
      await load();
    }
  });

  async function backupCard() {
    const box = el('div');
    const inner = el('div');
    box.appendChild(App.section('النسخ الاحتياطي', inner,
      [{ label: 'نسخة احتياطية فورية', type: 'gold', onClick: async () => {
          try {
            const r = await App.api.post('/api/backup/run');
            App.toast(r.message || 'تم إنشاء نسخة', 'success');
            await fill();
          } catch (e) { App.toastError(e); }
        } }]));

    async function fill() {
      inner.innerHTML = '';
      inner.appendChild(el('div', 'note',
        'النسخ الاحتياطي التلقائي يعمل يومياً في الثانية صباحاً، ويمكنك أخذ نسخة فورية في أي وقت.'));
      try {
        const list = await App.api.get('/api/backup');
        inner.appendChild(App.table({
          columns: [
            { key: 'at', label: 'الوقت', format: v => fmt.dateTime(v) },
            { key: 'sizeBytes', label: 'الحجم', align: 'num',
              format: v => v < 1048576 ? Math.round(v / 1024) + ' ك.ب' : (v / 1048576).toFixed(1) + ' م.ب' },
            { key: 'path', label: 'المسار' }
          ],
          rows: list,
          empty: 'لا توجد نسخ سابقة'
        }));
      } catch (e) { inner.appendChild(App.empty('تعذّر تحميل قائمة النسخ', 'warning')); }
    }
    await fill();
    return box;
  }

  // ===================== التقارير =====================

  App.registerPage('reports', {
    title: 'التقارير',
    icon: 'bar_chart',
    permission: 'REPORTS_VIEW',
    render: async (root) => {
      const state = { kind: 'financial', from: '', to: '' };

      root.appendChild(el('div', 'page-head',
        `<div><h2>التقارير</h2><p class="page-sub">تقارير المكتب مع التصدير والطباعة</p></div>`));

      const bar = el('div', 'filters');
      const wk = el('label', 'fld');
      wk.innerHTML = '<span>التقرير</span>';
      const sel = el('select');
      [['financial', 'التقرير المالي'], ['cases', 'تقرير القضايا'],
       ['consultants', 'تقرير المستشارين'], ['team', 'تقرير الفريق']]
        .forEach(([v, l]) => sel.appendChild(new Option(l, v)));
      wk.appendChild(sel);

      const wf = el('label', 'fld');
      wf.innerHTML = '<span>من تاريخ</span>';
      const inF = el('input'); inF.type = 'date';
      wf.appendChild(inF);

      const wt = el('label', 'fld');
      wt.innerHTML = '<span>إلى تاريخ</span>';
      const inT = el('input'); inT.type = 'date';
      wt.appendChild(inT);

      bar.appendChild(wk); bar.appendChild(wf); bar.appendChild(wt);
      root.appendChild(bar);

      const box = el('div');
      root.appendChild(box);

      [sel, inF, inT].forEach(n => n.onchange = () => {
        state.kind = sel.value; state.from = inF.value; state.to = inT.value; load();
      });

      async function load() {
        const stop = App.spinner(box);
        try {
          const r = await App.api.get('/api/reports/' + state.kind,
            { from: state.from, to: state.to });
          box.innerHTML = '';

          if (r.totals && Object.keys(r.totals).length) {
            const m = el('div', 'metrics');
            const labels = { claimed: 'إجمالي المطالبات', collected: 'المحصّل',
              remaining: 'المتبقي', collectionRate: 'نسبة التحصيل %',
              count: 'عدد السجلات', avgDays: 'متوسط المدة (يوم)', executions: 'ملفات التنفيذ' };
            Object.entries(r.totals).forEach(([k, v]) => {
              if (k === 'currency') return;
              const c = el('div', 'metric');
              const isMoney = ['claimed', 'collected', 'remaining'].includes(k);
              c.innerHTML = `<div class="metric-label">${esc(labels[k] || k)}</div>
                <div class="metric-value" style="font-size:20px">${
                  isMoney ? esc(fmt.money(v)) : esc(String(v))}</div>`;
              m.appendChild(c);
            });
            box.appendChild(m);
          }

          if ((r.chart || []).length) {
            box.appendChild(App.section('الرسم البياني', bars(r.chart)));
          }

          const acts = [
            { label: 'تصدير Excel', onClick: () => {
                const p = new URLSearchParams();
                if (state.from) p.append('from', state.from);
                if (state.to) p.append('to', state.to);
                App.api.download(`/api/reports/${state.kind}/export?` + p.toString(),
                  r.title + '.xlsx');
              } },
            { label: 'طباعة', onClick: () => printReport(r) }
          ];

          box.appendChild(App.section(r.title, App.table({
            columns: r.headers.map((h, i) => ({
              key: String(i), label: h,
              align: typeof (r.rows[0] || [])[i] === 'number' ? 'num' : ''
            })),
            rows: r.rows.map(row => {
              const o = {};
              row.forEach((cell, i) => { o[String(i)] = cell; });
              return o;
            }),
            empty: 'لا توجد بيانات في هذه الفترة'
          }), acts));
        } catch (e) {
          box.innerHTML = '';
          box.appendChild(App.empty(e.message || 'تعذّر تحميل التقرير', 'warning'));
        } finally { stop(); }
      }
      await load();
    }
  });

  function bars(points) {
    const box = el('div');
    const max = Math.max(...points.map(p => p.value), 1);
    const W = 640, H = 210, pad = 34;
    const bw = (W - pad * 2) / Math.max(points.length, 1);
    let svg = `<svg viewBox="0 0 ${W} ${H}" style="width:100%;height:auto">`;
    svg += `<line x1="${pad}" y1="${H - 30}" x2="${W - pad}" y2="${H - 30}" stroke="#e5e7eb"/>`;
    points.forEach((p, i) => {
      const h = Math.round((p.value / max) * (H - 74));
      const x = pad + i * bw + bw * 0.18;
      const w = bw * 0.64;
      const y = H - 30 - h;
      svg += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="4" fill="${p.color || '#0f2c4c'}"/>`;
      svg += `<text x="${x + w / 2}" y="${y - 5}" text-anchor="middle" font-size="11.5" font-weight="700" fill="#0f2c4c">${p.value}</text>`;
      svg += `<text x="${x + w / 2}" y="${H - 11}" text-anchor="middle" font-size="10.5" fill="#6b7280">${esc(String(p.label).slice(0, 14))}</text>`;
    });
    svg += '</svg>';
    box.innerHTML = svg;
    return box;
  }

  function printReport(r) {
    const head = r.headers.map(h => `<th>${esc(h)}</th>`).join('');
    const body = r.rows.map(row =>
      '<tr>' + row.map(c => `<td>${esc(c === null || c === undefined ? '' : c)}</td>`).join('') + '</tr>'
    ).join('');
    App.printHtml(r.title, `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`);
  }
})();
