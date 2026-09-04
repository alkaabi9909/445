/* =====================================================================
   المكتبة القانونية — هرم: تشريع ← باب ← فصل ← مادة.
   ===================================================================== */

(function () {
  const { el, esc } = App;

  App.registerPage('laws', {
    title: 'المكتبة القانونية',
    icon: 'menu_book',
    permission: 'ARCHIVE_VIEW',
    render: async (root, params) => renderLaws(root, params)
  });

  async function renderLaws(root, params) {
    const codes = await App.api.get('/api/laws');

    const head = el('div', 'page-head');
    head.innerHTML = `<div><h2>المكتبة القانونية</h2>
      <p class="page-sub">قوانين منظّمة هرمياً: باب ← فصل ← مادة</p></div>`;
    if (App.can('ARCHIVE_MANAGE')) {
      const b = el('button', 'btn btn-gold', 'إضافة تشريع');
      b.onclick = () => openCode();
      head.appendChild(b);
    }
    root.appendChild(head);

    if (!codes.length) {
      root.appendChild(App.empty('لا توجد تشريعات في المكتبة', 'book'));
      return;
    }

    // اختيار التشريع + البحث
    const bar = el('div', 'filters');
    const wCode = el('label', 'fld');
    wCode.innerHTML = '<span>التشريع</span>';
    const selCode = el('select');
    codes.forEach(c => selCode.appendChild(new Option(c.title, c.id)));
    if (params && params.id) selCode.value = params.id;
    wCode.appendChild(selCode);

    const wSearch = el('label', 'fld grow');
    wSearch.innerHTML = '<span>بحث في نصوص المواد</span>';
    const inSearch = el('input');
    inSearch.type = 'text';
    inSearch.placeholder = 'ابحث في نص المادة أو رقمها أو كلماتها المفتاحية';
    wSearch.appendChild(inSearch);

    bar.appendChild(wCode);
    bar.appendChild(wSearch);
    root.appendChild(bar);

    const layout = el('div');
    layout.style.cssText = 'display:grid;grid-template-columns:minmax(260px,340px) 1fr;gap:16px;align-items:start';
    const treeBox = el('div', 'card');
    const viewBox = el('div', 'card');
    treeBox.style.maxHeight = '72vh';
    treeBox.style.overflowY = 'auto';
    layout.appendChild(treeBox);
    layout.appendChild(viewBox);
    root.appendChild(layout);

    let current = null;

    async function loadTree() {
      treeBox.innerHTML = '<div class="spinner">جارٍ التحميل…</div>';
      viewBox.innerHTML = '';
      try {
        const t = await App.api.get(`/api/laws/${selCode.value}/tree`);
        treeBox.innerHTML = '';
        const head2 = el('div', 'card-head');
        head2.innerHTML = `<h3>${esc(t.code.title)}</h3>`;
        if (App.can('ARCHIVE_MANAGE')) {
          const add = el('button', 'btn btn-sm btn-ghost', '+ باب');
          add.onclick = () => openChapter(t.code.id, null, loadTree);
          head2.appendChild(add);
        }
        treeBox.appendChild(head2);

        const body = el('div', 'card-body');
        if (t.code.lawNumber) {
          body.appendChild(el('div', 'muted', esc(t.code.lawNumber)));
        }
        (t.chapters || []).forEach(ch => body.appendChild(chapterNode(ch, t.code.id, loadTree, show, 0)));
        (t.looseArticles || []).forEach(a => body.appendChild(articleLink(a, show, 0)));
        if (!(t.chapters || []).length && !(t.looseArticles || []).length) {
          body.appendChild(App.empty('لا توجد أبواب أو مواد بعد', 'description'));
        }
        treeBox.appendChild(body);

        viewBox.innerHTML = '';
        viewBox.appendChild(el('div', 'card-body',
          '<div class="empty"><span class="empty-icon">📖</span><div>اختر مادة من الشجرة لعرض نصها</div></div>'));
      } catch (e) {
        treeBox.innerHTML = '';
        treeBox.appendChild(App.empty(e.message || 'تعذّر تحميل التشريع', 'warning'));
      }
    }

    function show(article, path) {
      current = article;
      viewBox.innerHTML = '';
      const head3 = el('div', 'card-head');
      head3.innerHTML = `<h3>المادة ${esc(article.articleNumber)}${
        article.title ? ' — ' + esc(article.title) : ''}</h3>`;
      const acts = el('div', 'card-actions');
      const copy = el('button', 'btn btn-sm btn-ghost', 'نسخ النص');
      copy.onclick = () => {
        navigator.clipboard.writeText(article.articleText || '')
          .then(() => App.toast('نُسخ نص المادة', 'success'))
          .catch(() => App.toast('تعذّر النسخ', 'error'));
      };
      const pr = el('button', 'btn btn-sm btn-ghost', 'طباعة');
      pr.onclick = () => App.printHtml('المادة ' + article.articleNumber, `
        ${path ? `<div class="muted">${esc(path)}</div>` : ''}
        ${article.title ? `<h3>${esc(article.title)}</h3>` : ''}
        <p style="white-space:pre-wrap;line-height:2.2;font-size:15px">${esc(article.articleText)}</p>`);
      acts.appendChild(copy);
      acts.appendChild(pr);
      head3.appendChild(acts);
      viewBox.appendChild(head3);

      const body = el('div', 'card-body');
      if (path) {
        body.appendChild(el('div', 'muted', esc(path)));
        body.appendChild(el('div', 'mb-2'));
      }
      const p = el('p');
      p.style.cssText = 'white-space:pre-wrap;line-height:2.3;font-size:15.5px';
      p.textContent = article.articleText || '';
      body.appendChild(p);

      if (article.keywords) {
        const chips = el('div', 'chips');
        chips.style.marginTop = '14px';
        article.keywords.split(/\s+/).filter(Boolean).forEach(k => {
          const c = el('span', 'chip', esc(k));
          c.onclick = () => { inSearch.value = k; doSearch(); };
          chips.appendChild(c);
        });
        body.appendChild(chips);
      }
      viewBox.appendChild(body);
    }

    let timer = null;
    inSearch.addEventListener('input', () => {
      clearTimeout(timer);
      timer = setTimeout(doSearch, 320);
    });

    async function doSearch() {
      const q = inSearch.value.trim();
      if (!q) { loadTree(); return; }
      treeBox.innerHTML = '<div class="spinner">جارٍ البحث…</div>';
      try {
        const hits = await App.api.get('/api/laws/search', { q });
        treeBox.innerHTML = '';
        treeBox.appendChild(el('div', 'card-head', `<h3>نتائج البحث (${hits.length})</h3>`));
        const body = el('div', 'card-body');
        if (!hits.length) body.appendChild(App.empty('لا توجد مواد مطابقة', 'search'));
        hits.forEach(h => {
          const item = el('div', 'notif-item');
          item.innerHTML = `<div class="notif-title">المادة ${esc(h.articleNumber)}${
            h.title ? ' — ' + esc(h.title) : ''}</div>
            <div class="notif-msg">${App.highlight((h.articleText || '').slice(0, 160), q)}</div>
            <div class="notif-time">${esc(h.lawTitle || '')}${h.chapterTitle ? ' ← ' + esc(h.chapterTitle) : ''}</div>`;
          item.onclick = () => show({
            id: h.articleId, articleNumber: h.articleNumber, title: h.title,
            articleText: h.articleText, keywords: ''
          }, (h.lawTitle || '') + (h.chapterTitle ? ' ← ' + h.chapterTitle : ''));
          body.appendChild(item);
        });
        treeBox.appendChild(body);
      } catch (e) {
        treeBox.innerHTML = '';
        treeBox.appendChild(App.empty(e.message || 'تعذّر البحث', 'warning'));
      }
    }

    selCode.onchange = () => { inSearch.value = ''; loadTree(); };
    await loadTree();
  }

  /** عقدة باب أو فصل قابلة للطي. */
  function chapterNode(ch, codeId, reload, show, depth) {
    const wrap = el('div');
    wrap.style.marginInlineStart = (depth * 12) + 'px';

    const header = el('div');
    header.style.cssText = 'display:flex;align-items:center;gap:7px;padding:6px 4px;cursor:pointer;font-weight:700';
    const count = countArticles(ch);
    const caret = el('span', null, '▾');
    header.appendChild(caret);
    header.appendChild(el('span', null, ch.level === 'BAB' ? '📕' : '📗'));
    header.appendChild(el('span', null,
      `${esc(ch.chapterNumber || '')} ${esc(ch.title)} <span class="badge muted">${count}</span>`));
    wrap.appendChild(header);

    const kids = el('div');
    (ch.children || []).forEach(c => kids.appendChild(chapterNode(c, codeId, reload, show, depth + 1)));
    (ch.articles || []).forEach(a => kids.appendChild(articleLink(a, show, depth + 1, ch.title)));

    if (App.can('ARCHIVE_MANAGE')) {
      const acts = el('div', 'row');
      acts.style.cssText = 'gap:5px;margin:5px 0 8px ' + ((depth + 1) * 12) + 'px';
      if (ch.level === 'BAB') {
        const f = el('button', 'btn btn-sm btn-ghost', '+ فصل');
        f.onclick = (e) => { e.stopPropagation(); openChapter(codeId, ch.id, reload); };
        acts.appendChild(f);
      }
      const a = el('button', 'btn btn-sm btn-ghost', '+ مادة');
      a.onclick = (e) => { e.stopPropagation(); openArticle(codeId, ch.id, reload); };
      acts.appendChild(a);
      kids.appendChild(acts);
    }

    wrap.appendChild(kids);
    header.onclick = () => {
      const hidden = kids.style.display === 'none';
      kids.style.display = hidden ? '' : 'none';
      caret.textContent = hidden ? '▾' : '◂';
    };
    return wrap;
  }

  function countArticles(ch) {
    let n = (ch.articles || []).length;
    (ch.children || []).forEach(c => { n += countArticles(c); });
    return n;
  }

  function articleLink(a, show, depth, parentTitle) {
    const d = el('div');
    d.style.cssText = 'padding:5px 4px;cursor:pointer;margin-inline-start:' + (depth * 12) + 'px';
    d.innerHTML = `<span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle">description</span> المادة ${esc(a.articleNumber)}${a.title ? ' — ' + esc(a.title) : ''}`;
    d.onmouseenter = () => d.style.background = 'var(--gold-pale)';
    d.onmouseleave = () => d.style.background = '';
    d.onclick = () => show(a, parentTitle || '');
    return d;
  }

  // ---------- الإضافة ----------

  function openCode() {
    const f = App.form([
      { key: 'title', label: 'عنوان التشريع', type: 'text', required: true, col: 2 },
      { key: 'lawNumber', label: 'رقم القانون', type: 'text' },
      { key: 'issueYear', label: 'سنة الإصدار', type: 'number' },
      { key: 'jurisdiction', label: 'الجهة', type: 'text' },
      { key: 'description', label: 'الوصف', type: 'textarea' }
    ], {});
    App.modal({
      title: 'إضافة تشريع', width: 'wide', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            await App.api.post('/api/laws', f.read());
            App.closeModal(); App.toast('تمت الإضافة', 'success'); App.route();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  function openChapter(codeId, parentId, reload) {
    const f = App.form([
      { key: 'chapterNumber', label: parentId ? 'رقم الفصل' : 'رقم الباب', type: 'text' },
      { key: 'sortOrder', label: 'الترتيب', type: 'number' },
      { key: 'title', label: 'العنوان', type: 'text', required: true, col: 2 }
    ], { sortOrder: 1 });
    App.modal({
      title: parentId ? 'إضافة فصل' : 'إضافة باب', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const v = f.read();
            v.parentId = parentId;
            await App.api.post(`/api/laws/${codeId}/chapters`, v);
            App.closeModal(); App.toast('تمت الإضافة', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }

  function openArticle(codeId, chapterId, reload) {
    const f = App.form([
      { key: 'articleNumber', label: 'رقم المادة', type: 'text', required: true },
      { key: 'sortOrder', label: 'الترتيب', type: 'number' },
      { key: 'title', label: 'عنوان المادة', type: 'text', col: 2 },
      { key: 'articleText', label: 'نص المادة', type: 'textarea', required: true, rows: 8 },
      { key: 'keywords', label: 'الكلمات المفتاحية', type: 'text', col: 2 }
    ], { sortOrder: 1 });
    App.modal({
      title: 'إضافة مادة', width: 'wide', bodyNode: f.node,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const v = f.read();
            v.chapterId = chapterId;
            await App.api.post(`/api/laws/${codeId}/articles`, v);
            App.closeModal(); App.toast('تمت إضافة المادة', 'success'); reload();
          } },
        { label: 'إلغاء', onClick: () => App.closeModal() }
      ]
    });
  }
})();
