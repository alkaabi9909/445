/* =====================================================================
   الأرشيف الشامل والبحث المتقدم — يعمل بالكامل بالعربية بلا إنترنت.
   ===================================================================== */

(function () {
  const { el, esc, fmt } = App;

  App.registerPage('archive', {
    title: 'الأرشيف والبحث',
    icon: 'inventory_2',
    permission: 'ARCHIVE_VIEW',
    render: async (root) => renderArchive(root)
  });

  async function renderArchive(root) {
    const state = { q: '', type: '', category: '', from: '', to: '', page: 0, size: 20 };

    const head = el('div', 'page-head');
    head.innerHTML = `<div><h2>الأرشيف والبحث المتقدم</h2>
      <p class="page-sub">كل ملف مغلق يُحفظ آلياً بمستنداته — والبحث يعمل بالعربية دون إنترنت</p></div>`;
    const btns = el('div', 'row');
    const exp = el('button', 'btn btn-ghost', 'تصدير Excel');
    exp.onclick = () => App.api.download('/api/archive/export' + query(state), 'الأرشيف.xlsx');
    btns.appendChild(exp);
    if (App.can('ARCHIVE_MANAGE')) {
      const add = el('button', 'btn btn-gold', 'إضافة عنصر');
      add.onclick = () => openItem(null, load);
      btns.appendChild(add);
    }
    head.appendChild(btns);
    root.appendChild(head);

    // شريط البحث
    const searchBar = el('div', 'card');
    const sb = el('div', 'card-body');
    const big = el('input');
    big.type = 'text';
    big.placeholder = 'ابحث في الأرشيف بالعربية… (العنوان، المحتوى، الكلمات المفتاحية، الموكل، رقم الملف)';
    big.style.cssText = 'font-size:16px;padding:12px 15px';
    sb.appendChild(big);

    const filters = el('div', 'row');
    filters.style.marginTop = '11px';
    const selType = el('select');
    selType.appendChild(new Option('كل الأنواع', ''));
    App.options('archiveType').forEach(o => selType.appendChild(new Option(o.label, o.value)));
    const inFrom = el('input'); inFrom.type = 'date';
    const inTo = el('input'); inTo.type = 'date';
    const inCat = el('input'); inCat.type = 'text'; inCat.placeholder = 'التصنيف';
    [['النوع', selType], ['التصنيف', inCat], ['من تاريخ', inFrom], ['إلى تاريخ', inTo]]
      .forEach(([label, node]) => {
        const w = el('label', 'fld');
        w.style.cssText = 'margin:0;min-width:160px;flex:1';
        w.innerHTML = `<span>${label}</span>`;
        w.appendChild(node);
        filters.appendChild(w);
      });
    sb.appendChild(filters);

    // رقائق البحوث
    const chipsBox = el('div');
    chipsBox.style.marginTop = '11px';
    sb.appendChild(chipsBox);

    searchBar.appendChild(sb);
    root.appendChild(searchBar);

    const results = el('div');
    root.appendChild(results);

    let timer = null;
    big.addEventListener('input', () => {
      clearTimeout(timer);
      timer = setTimeout(() => { state.q = big.value.trim(); state.page = 0; load(); }, 300);
    });
    [selType, inCat, inFrom, inTo].forEach(n => n.onchange = () => {
      state.type = selType.value; state.category = inCat.value.trim();
      state.from = inFrom.value; state.to = inTo.value;
      state.page = 0; load();
    });

    async function loadChips() {
      chipsBox.innerHTML = '';
      try {
        const s = await App.api.get('/api/searches');
        const wrap = el('div', 'chips');

        (s.saved || []).forEach(x => {
          const c = el('span', 'chip');
          c.innerHTML = `⭐ ${esc(x.name)}`;
          const del = el('span', 'x', '✕');
          del.onclick = async (e) => {
            e.stopPropagation();
            try { await App.api.del('/api/searches/' + x.id); loadChips(); } catch (err) { App.toastError(err); }
          };
          c.appendChild(del);
          c.onclick = () => { big.value = x.queryText || ''; state.q = big.value; state.page = 0; load(); };
          wrap.appendChild(c);
        });

        (s.recent || []).forEach(x => {
          if (!x.queryText) return;
          const c = el('span', 'chip');
          c.innerHTML = `🕘 ${esc(x.queryText)}`;
          c.onclick = () => { big.value = x.queryText; state.q = x.queryText; state.page = 0; load(); };
          wrap.appendChild(c);
        });

        const save = el('span', 'chip');
        save.innerHTML = '💾 حفظ هذا البحث';
        save.onclick = () => {
          if (!state.q) { App.toast('اكتب نص البحث أولاً', 'error'); return; }
          const f = App.form([{ key: 'name', label: 'اسم البحث', type: 'text', required: true, col: 2 }], {});
          App.modal({
            title: 'حفظ البحث', bodyNode: f.node, width: 'narrow',
            actions: [
              { label: 'حفظ', type: 'gold', onClick: async () => {
                  await App.api.post('/api/searches',
                    { name: f.read().name, queryText: state.q, filtersJson: JSON.stringify(state) });
                  App.closeModal(); App.toast('تم حفظ البحث', 'success'); loadChips();
                } },
              { label: 'إلغاء', onClick: () => App.closeModal() }
            ]
          });
        };
        wrap.appendChild(save);
        chipsBox.appendChild(wrap);
      } catch (e) { /* تجاهل */ }
    }

    async function load() {
      const stop = App.spinner(results);
      try {
        const res = await App.api.get('/api/archive', state);
        results.innerHTML = '';
        const items = res.items || [];

        if (state.q) {
          // تسجيل البحث في آخر خمسة
          App.api.post('/api/searches', { queryText: state.q }).then(loadChips).catch(() => {});
        }

        if (!items.length) {
          results.appendChild(App.empty('لا توجد نتائج مطابقة للبحث', 'search'));
        } else {
          const grid = el('div');
          items.forEach(a => grid.appendChild(card(a, state.q, load)));
          results.appendChild(grid);
        }

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
          p.appendChild(el('span', 'muted', `صفحة ${state.page + 1} من ${pages} — ${total} نتيجة`));
          p.appendChild(prev);
        } else {
          p.innerHTML = `<span class="muted">${total} نتيجة</span>`;
        }
        results.appendChild(p);
      } catch (e) {
        results.innerHTML = '';
        results.appendChild(App.empty(e.message || 'تعذّر البحث', 'warning'));
      } finally { stop(); }
    }

    await loadChips();
    await load();
  }

  function query(state) {
    const p = new URLSearchParams();
    ['q', 'type', 'category', 'from', 'to'].forEach(k => { if (state[k]) p.append(k, state[k]); });
    const s = p.toString();
    return s ? '?' + s : '';
  }

  function card(a, needle, reload) {
    const c = el('div', 'card');
    const body = el('div', 'card-body');
    const linkable = a.sourceType && a.sourceId;

    body.innerHTML = `<div class="row between mb-1">
        <div>${App.badge(a.category || '—', 'gold')}
          <strong style="margin-inline-start:8px">${App.highlight(a.title, needle)}</strong></div>
        <span class="muted nowrap">${esc(a.referenceNo || '')}</span>
      </div>
      <div style="color:#4b5563">${App.highlight(
        (a.summary || a.content || '').slice(0, 260), needle)}</div>
      <div class="muted" style="margin-top:7px;font-size:12.5px;display:flex;align-items:center;gap:8px;flex-wrap:wrap">
        ${a.itemDate ? '<span style="display:inline-flex;align-items:center;gap:3px">' + App.icon('calendar', 12) + ' ' + fmt.date(a.itemDate) + '</span>' : ''}
        ${a.clientName ? '<span style="display:inline-flex;align-items:center;gap:3px">' + App.icon('person', 12) + ' ' + esc(a.clientName) + '</span>' : ''}
        ${a.sourceNumber ? '<span style="display:inline-flex;align-items:center;gap:3px">' + App.icon('folder', 12) + ' ' + esc(a.sourceNumber) + '</span>' : ''}
        ${a.courtName ? '<span style="display:inline-flex;align-items:center;gap:3px">' + App.icon('balance', 12) + ' ' + esc(a.courtName) + '</span>' : ''}
      </div>`;

    const row = el('div', 'row');
    row.style.marginTop = '9px';
    if (linkable) {
      const open = el('button', 'btn btn-sm btn-ghost', 'فتح الملف الأصلي');
      open.onclick = () => {
        const page = { FINANCIAL_FILE: 'financial', CASE: 'cases',
          EXECUTION: 'execution', CONSULTATION: 'consultations' }[a.sourceType];
        if (page) App.go(page, { id: a.sourceId });
      };
      row.appendChild(open);
    }
    const pr = el('button', 'btn btn-sm btn-ghost', 'طباعة');
    pr.onclick = () => App.printHtml(a.title, `
      <div class="kv"><b>المرجع</b><span>${esc(a.referenceNo || '')}</span></div>
      <div class="kv"><b>النوع</b><span>${esc(a.category || '')}</span></div>
      <div class="kv"><b>التاريخ</b><span>${fmt.date(a.itemDate)}</span></div>
      ${a.clientName ? `<div class="kv"><b>الموكل</b><span>${esc(a.clientName)}</span></div>` : ''}
      <p style="white-space:pre-wrap;line-height:2;margin-top:18px">${esc(a.content || '')}</p>`);
    row.appendChild(pr);

    if (App.can('ARCHIVE_MANAGE')) {
      const ed = el('button', 'btn btn-sm btn-ghost', 'تعديل');
      ed.onclick = () => openItem(a, reload);
      row.appendChild(ed);
      if (!a.sourceType) {
        const del = el('button', 'btn btn-sm btn-danger', 'حذف');
        del.onclick = async () => {
          if (!await App.confirm('حذف العنصر «' + a.title + '» من الأرشيف؟')) return;
          try { await App.api.del('/api/archive/' + a.id); App.toast('تم الحذف', 'success'); reload(); }
          catch (e) { App.toastError(e); }
        };
        row.appendChild(del);
      }
    }
    body.appendChild(row);
    c.appendChild(body);
    return c;
  }

  function openItem(a, reload) {
    const f = App.form([
      { key: 'itemType', label: 'النوع', type: 'select', required: true, options: App.options('archiveType') },
      { key: 'category', label: 'التصنيف', type: 'text' },
      { key: 'title', label: 'العنوان', type: 'text', required: true, col: 2 },
      { key: 'summary', label: 'الملخص', type: 'textarea' },
      { key: 'content', label: 'المحتوى', type: 'textarea', rows: 8 },
      { key: 'keywords', label: 'الكلمات المفتاحية', type: 'text', col: 2,
        hint: 'كلمات عربية تفصلها مسافات لتسهيل البحث' },
      { key: 'itemDate', label: 'التاريخ', type: 'date' },
      { key: 'courtName', label: 'المحكمة', type: 'text' },
      { key: 'clientName', label: 'الموكل', type: 'text' },
      { key: 'confidential', label: 'سري', type: 'checkbox' }
    ], a || { itemDate: fmt.today() });

    App.modal({
      title: a ? 'تعديل عنصر الأرشيف' : 'إضافة عنصر للأرشيف',
      width: 'wide', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const v = f.read();
            const r = a ? await App.api.put('/api/archive/' + a.id, v)
                        : await App.api.post('/api/archive', v);
            App.closeModal(); App.toast(r.message || 'تم الحفظ', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }
})();
