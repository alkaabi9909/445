/* =====================================================================
   نظام قانون — نواة الواجهة
   يوفّر: عميل REST، الموجّه، الصلاحيات، الجداول، النماذج، النوافذ، الطباعة.
   كل صفحات النظام تُبنى على الكائن App المعرّف هنا.
   ===================================================================== */

const App = (function () {

  const state = { user: null, permissions: [], lookups: {}, ready: false };
  const pages = new Map();
  let notifTimer = null;

  // ===================== أدوات عامة =====================

  const el = (tag, cls, html) => {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html !== undefined && html !== null) n.innerHTML = html;
    return n;
  };

  /** يمنع حقن HTML من البيانات القادمة من الخادم. */
  const esc = (s) => {
    if (s === null || s === undefined) return '';
    return String(s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  };

  const pad2 = (n) => String(n).padStart(2, '0');

  const fmt = {
    money(n) {
      if (n === null || n === undefined || n === '') return '—';
      const v = Number(n);
      if (isNaN(v)) return '—';
      return v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' د.إ';
    },
    num(n) {
      if (n === null || n === undefined || n === '') return '—';
      const v = Number(n);
      return isNaN(v) ? '—' : v.toLocaleString('en-US');
    },
    date(d) {
      if (!d) return '—';
      const s = String(d).slice(0, 10).split('-');
      if (s.length !== 3) return String(d);
      return `${s[2]}/${s[1]}/${s[0]}`;
    },
    dateTime(d) {
      if (!d) return '—';
      const str = String(d);
      const datePart = fmt.date(str);
      const t = str.length > 10 ? str.slice(11, 16) : '';
      return t ? `${datePart} — ${t}` : datePart;
    },
    /** فرق الأيام من اليوم (موجب = في المستقبل). */
    daysFromToday(d) {
      if (!d) return null;
      const target = new Date(String(d).slice(0, 10) + 'T00:00:00');
      const now = new Date();
      now.setHours(0, 0, 0, 0);
      return Math.round((target - now) / 86400000);
    },
    today() {
      const n = new Date();
      return `${n.getFullYear()}-${pad2(n.getMonth() + 1)}-${pad2(n.getDate())}`;
    }
  };

  // ===================== عميل REST =====================

  async function request(method, path, body, isForm) {
    const opts = { method, credentials: 'include', headers: {} };
    if (body !== undefined && body !== null) {
      if (isForm) {
        opts.body = body;
      } else {
        opts.headers['Content-Type'] = 'application/json;charset=UTF-8';
        opts.body = JSON.stringify(body);
      }
    }

    let res;
    try {
      res = await fetch(path, opts);
    } catch (e) {
      throw { message: 'تعذّر الاتصال بالخادم — تأكد أن النظام يعمل.', blockers: [] };
    }

    if (res.status === 401) {
      showLogin('انتهت الجلسة، يرجى تسجيل الدخول مجدداً');
      throw { message: 'انتهت الجلسة، يرجى تسجيل الدخول مجدداً', blockers: [] };
    }

    const ctype = res.headers.get('content-type') || '';
    let payload = null;
    if (ctype.includes('application/json')) {
      payload = await res.json().catch(() => null);
    } else if (!ctype.includes('spreadsheet') && !ctype.includes('octet-stream')) {
      payload = await res.text().catch(() => null);
    }

    if (!res.ok) {
      const msg = (payload && (payload.error || payload.message))
        || (res.status === 403 ? 'لا تملك صلاحية هذا الإجراء' : 'حدث خطأ غير متوقع');
      throw { message: msg, blockers: (payload && payload.blockers) || [], status: res.status };
    }
    return payload;
  }

  const qs = (query) => {
    if (!query) return '';
    const p = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') p.append(k, v);
    });
    const s = p.toString();
    return s ? '?' + s : '';
  };

  const api = {
    get: (path, query) => request('GET', path + qs(query)),
    post: (path, body) => request('POST', path, body === undefined ? {} : body),
    put: (path, body) => request('PUT', path, body === undefined ? {} : body),
    del: (path) => request('DELETE', path),
    upload: (path, formData) => request('POST', path, formData, true),

    /** تنزيل ملف (Excel أو مرفق) باسم عربي. */
    async download(path, filenameAr) {
      let res;
      try {
        res = await fetch(path, { credentials: 'include' });
      } catch (e) {
        toast('تعذّر الاتصال بالخادم', 'error');
        return;
      }
      if (!res.ok) {
        toast(res.status === 403 ? 'لا تملك صلاحية هذا الإجراء' : 'تعذّر تنزيل الملف', 'error');
        return;
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filenameAr || 'ملف';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 2000);
    }
  };

  // ===================== الصلاحيات =====================

  const can = (permission) => !permission || state.permissions.includes(permission);

  // ===================== التنبيهات العائمة =====================

  function toast(message, type) {
    const root = document.getElementById('toast-root');
    const t = el('div', 'toast' + (type ? ' ' + type : ''));
    t.textContent = message;
    root.appendChild(t);
    setTimeout(() => t.remove(), type === 'error' ? 6000 : 3500);
  }

  /** يعرض رسالة الخطأ ومعها الموانع نصاً إن وُجدت. */
  function toastError(err) {
    const root = document.getElementById('toast-root');
    const t = el('div', 'toast error');
    t.appendChild(el('div', null, esc(err && err.message ? err.message : 'حدث خطأ')));
    if (err && err.blockers && err.blockers.length) {
      const ul = el('ul');
      err.blockers.forEach(b => { const li = el('li'); li.textContent = b; ul.appendChild(li); });
      t.appendChild(ul);
    }
    root.appendChild(t);
    setTimeout(() => t.remove(), 8000);
  }

  // ===================== النوافذ المنبثقة =====================

  function modal(opts) {
    const root = document.getElementById('modal-root');
    const box = document.getElementById('modal');
    const foot = document.getElementById('modal-foot');
    const bodyEl = document.getElementById('modal-body');

    document.getElementById('modal-title').textContent = opts.title || '';
    bodyEl.innerHTML = '';
    if (opts.bodyNode) bodyEl.appendChild(opts.bodyNode);
    else if (opts.bodyHtml) bodyEl.innerHTML = opts.bodyHtml;

    box.className = 'modal' + (opts.width === 'wide' ? ' wide' : opts.width === 'narrow' ? ' narrow' : '');

    foot.innerHTML = '';
    (opts.actions || []).forEach(a => {
      const b = el('button', 'btn ' + (a.type ? 'btn-' + a.type : 'btn-ghost'));
      b.textContent = a.label;
      b.onclick = async () => {
        if (!a.onClick) { closeModal(); return; }
        b.disabled = true;
        try { await a.onClick(); }
        catch (e) { toastError(e); }
        finally { b.disabled = false; }
      };
      foot.appendChild(b);
    });

    root.hidden = false;
    return { close: closeModal };
  }

  function closeModal() {
    document.getElementById('modal-root').hidden = true;
    document.getElementById('modal-body').innerHTML = '';
  }

  function confirmBox(message) {
    return new Promise(resolve => {
      const body = el('div');
      body.appendChild(el('p', null, esc(message)));
      modal({
        title: 'تأكيد الإجراء',
        width: 'narrow',
        bodyNode: body,
        actions: [
          { label: 'تأكيد', type: 'gold', onClick: () => { closeModal(); resolve(true); } },
          { label: 'إلغاء', onClick: () => { closeModal(); resolve(false); } }
        ]
      });
    });
  }

  // ===================== النماذج =====================

  /**
   * يبني نموذجاً شبكياً ويعيد { node, read() }.
   * الأنواع: text number money date datetime textarea select checkbox file readonly
   */
  function form(fields, values) {
    const v = values || {};
    const node = el('div', 'form-grid');
    const inputs = {};

    fields.forEach(f => {
      const wrap = el('label', 'fld' + (f.col === 2 || f.type === 'textarea' ? ' col-2' : ''));
      const label = el('span');
      label.textContent = f.label;
      if (f.required) label.insertAdjacentHTML('beforeend', ' <span class="req">*</span>');
      wrap.appendChild(label);

      let input;
      const val = v[f.key];

      if (f.type === 'select') {
        input = el('select');
        input.appendChild(new Option(f.placeholder || '— اختر —', ''));
        (f.options || []).forEach(o => {
          const opt = new Option(o.label !== undefined ? o.label : o.name, o.value !== undefined ? o.value : o.id);
          input.appendChild(opt);
        });
        input.value = val === null || val === undefined ? '' : String(val);
      } else if (f.type === 'textarea') {
        input = el('textarea');
        input.value = val === null || val === undefined ? '' : val;
        if (f.rows) input.rows = f.rows;
      } else if (f.type === 'checkbox') {
        input = el('input');
        input.type = 'checkbox';
        input.checked = !!val;
        input.style.width = 'auto';
      } else if (f.type === 'readonly') {
        input = el('input');
        input.type = 'text';
        input.value = val === null || val === undefined ? '' : val;
        input.disabled = true;
      } else if (f.type === 'file') {
        input = el('input');
        input.type = 'file';
        if (f.accept) input.accept = f.accept;
      } else {
        input = el('input');
        input.type = f.type === 'money' || f.type === 'number' ? 'number'
          : f.type === 'date' ? 'date'
          : f.type === 'datetime' ? 'datetime-local'
          : 'text';
        if (f.type === 'money') input.step = '0.01';
        if (f.type === 'money' || f.type === 'number') input.min = f.min !== undefined ? f.min : '0';
        input.value = val === null || val === undefined ? '' : val;
      }

      if (f.disabled) input.disabled = true;
      if (f.onChange) input.addEventListener('change', () => f.onChange(readOne(f, input), inputs));

      wrap.appendChild(input);
      if (f.hint) wrap.appendChild(el('div', 'form-hint', esc(f.hint)));
      inputs[f.key] = input;
      node.appendChild(wrap);
    });

    function readOne(f, input) {
      if (f.type === 'checkbox') return input.checked;
      if (f.type === 'file') return input.files && input.files[0] ? input.files[0] : null;
      const raw = input.value;
      if (raw === '' || raw === null) return null;
      if (f.type === 'money' || f.type === 'number') return Number(raw);
      return raw;
    }

    return {
      node,
      inputs,
      read() {
        const out = {};
        for (const f of fields) {
          if (f.type === 'readonly') continue;
          const value = readOne(f, inputs[f.key]);
          if (f.required && (value === null || value === '' || value === false && f.type !== 'checkbox')) {
            inputs[f.key].focus();
            throw { message: 'الحقل مطلوب: ' + f.label, blockers: [] };
          }
          out[f.key] = value;
        }
        return out;
      }
    };
  }

  // ===================== الجداول =====================

  /** columns: [{key,label,format,align,width,noSort}] */
  function table(opts) {
    const cols = opts.columns || [];
    let rows = (opts.rows || []).slice();
    const wrap = el('div', 'table-wrap');

    if (!rows.length) {
      const e = el('div', 'empty');
      e.appendChild(el('span', 'empty-icon')).innerHTML = '<span class="material-symbols-outlined">table_chart</span>';
      e.appendChild(el('div', null, esc(opts.empty || 'لا توجد بيانات لعرضها')));
      return e;
    }

    let sortKey = null, sortDir = 1;
    const tbl = el('table', 'tbl');
    const thead = el('thead');
    const trh = el('tr');

    cols.forEach(c => {
      const th = el('th', (c.align === 'num' ? 'num ' : c.align === 'center' ? 'center ' : '') + (c.noSort ? 'no-sort' : ''));
      th.textContent = c.label;
      if (c.width) th.style.width = c.width;
      if (!c.noSort) {
        th.onclick = () => {
          if (sortKey === c.key) sortDir = -sortDir; else { sortKey = c.key; sortDir = 1; }
          rows.sort((a, b) => {
            const x = a[c.key], y = b[c.key];
            if (x === null || x === undefined) return 1;
            if (y === null || y === undefined) return -1;
            if (typeof x === 'number' && typeof y === 'number') return (x - y) * sortDir;
            return String(x).localeCompare(String(y), 'ar') * sortDir;
          });
          render();
        };
      }
      trh.appendChild(th);
    });
    thead.appendChild(trh);
    tbl.appendChild(thead);

    const tbody = el('tbody');
    tbl.appendChild(tbody);

    function render() {
      // علامة الفرز
      Array.from(trh.children).forEach((th, i) => {
        const c = cols[i];
        th.innerHTML = esc(c.label) + (sortKey === c.key
          ? ' <span class="sort-mark">' + (sortDir === 1 ? '▲' : '▼') + '</span>' : '');
      });

      tbody.innerHTML = '';
      rows.forEach(r => {
        const tr = el('tr', opts.onRow ? 'clickable' : '');
        cols.forEach(c => {
          const td = el('td', c.align === 'num' ? 'num' : c.align === 'center' ? 'center' : '');
          const content = c.format ? c.format(r[c.key], r) : (r[c.key] === null || r[c.key] === undefined ? '—' : r[c.key]);
          if (content instanceof Node) td.appendChild(content);
          else td.innerHTML = c.format ? content : esc(content);
          tr.appendChild(td);
        });
        if (opts.onRow) tr.onclick = () => opts.onRow(r);
        tbody.appendChild(tr);
      });
    }

    render();
    wrap.appendChild(tbl);
    return wrap;
  }

  // ===================== عناصر مساعدة =====================

  const badge = (text, tone) => `<span class="badge ${tone || 'muted'}">${esc(text)}</span>`;

  function section(titleAr, contentNode, actions) {
    const card = el('div', 'card');
    if (titleAr || (actions && actions.length)) {
      const head = el('div', 'card-head');
      head.appendChild(el('h3', null, esc(titleAr || '')));
      if (actions && actions.length) {
        const box = el('div', 'card-actions');
        actions.forEach(a => {
          if (a instanceof Node) { box.appendChild(a); return; }
          const b = el('button', 'btn btn-sm ' + (a.type ? 'btn-' + a.type : 'btn-ghost'));
          b.textContent = a.label;
          b.onclick = a.onClick;
          box.appendChild(b);
        });
        head.appendChild(box);
      }
      card.appendChild(head);
    }
    const body = el('div', 'card-body');
    if (contentNode) body.appendChild(contentNode);
    card.appendChild(body);
    return card;
  }

  function spinner(root) {
    const s = el('div', 'spinner', 'جارٍ التحميل…');
    root.innerHTML = '';
    root.appendChild(s);
    return () => s.remove();
  }

  function empty(message, iconName) {
    const e = el('div', 'empty');
    const iconEl = el('span', 'empty-icon');
    iconEl.innerHTML = `<span class="material-symbols-outlined">${iconName || 'description'}</span>`;
    e.appendChild(iconEl);
    e.appendChild(el('div', null, esc(message)));
    return e;
  }

  /** يرجع أيقونة Material Symbols بحجم مخصص. */
  function icon(name, size) {
    if (!name) return '';
    return `<span class="material-symbols-outlined" style="font-size:${size || 18}px">${name}</span>`;
  }

  /** يبرز كلمات البحث في نص. */
  function highlight(text, needle) {
    if (!text) return '';
    const safe = esc(text);
    if (!needle || !needle.trim()) return safe;
    const words = needle.trim().split(/\s+/).filter(w => w.length > 1).map(w =>
      w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
    if (!words.length) return safe;
    return safe.replace(new RegExp('(' + words.join('|') + ')', 'gi'), '<mark>$1</mark>');
  }

  // ===================== الطباعة =====================

  function printHtml(titleAr, innerHtml) {
    const office = (state.settings && state.settings.officeName) || 'مكتب المحاماة والاستشارات القانونية';
    const w = window.open('', '_blank', 'width=900,height=700');
    if (!w) { toast('يرجى السماح بالنوافذ المنبثقة للطباعة', 'error'); return; }
    w.document.write(`<!DOCTYPE html><html lang="ar" dir="rtl"><head><meta charset="UTF-8">
<title>${esc(titleAr)}</title><style>
body{font-family:"Segoe UI","Dubai",Tahoma,Arial,sans-serif;color:#1f2937;margin:0;line-height:1.8;}
.print-sheet{max-width:800px;margin:0 auto;padding:30px;}
.print-head{text-align:center;border-bottom:3px double #0f2c4c;padding-bottom:14px;margin-bottom:22px;}
.print-head h1{font-size:21px;margin:0 0 4px;color:#0f2c4c;}
.doc-title{font-size:17px;font-weight:700;margin-top:10px;}
table{width:100%;border-collapse:collapse;margin:12px 0;font-size:13px;}
th,td{border:1px solid #ccc;padding:7px 9px;text-align:right;}
th{background:#f0f3f7;color:#0f2c4c;}
.kv{display:flex;gap:10px;padding:5px 0;border-bottom:1px dotted #ddd;}
.kv b{min-width:150px;color:#0f2c4c;}
.print-foot{margin-top:34px;padding-top:14px;border-top:1px solid #ccc;font-size:11.5px;color:#666;display:flex;justify-content:space-between;}
.sign-row{display:flex;justify-content:space-between;margin-top:46px;}
.sign-box{text-align:center;min-width:190px;}
.sign-line{border-top:1px solid #333;margin-top:40px;padding-top:5px;font-size:12.5px;}
.amount-box{background:#faf3e3;border:1px solid #e5cd9a;padding:11px 14px;border-radius:8px;margin:12px 0;font-size:15px;font-weight:700;}
@media print{@page{margin:14mm;}}
</style></head><body><div class="print-sheet">
<div class="print-head"><h1>${esc(office)}</h1>
<div style="font-size:12px;color:#666;">نظام قانون — النظام المتكامل لإدارة مكاتب المحاماة</div>
<div class="doc-title">${esc(titleAr)}</div></div>
${innerHtml}
<div class="print-foot"><span>تاريخ الطباعة: ${fmt.date(fmt.today())}</span><span>${esc(office)}</span></div>
</div></body></html>`);
    w.document.close();
    setTimeout(() => { w.focus(); w.print(); }, 350);
  }

  // ===================== الصفحات والموجّه =====================

  function registerPage(key, def) {
    pages.set(key, Object.assign({ key }, def));
  }

  function buildSidebar() {
    const nav = document.getElementById('sidebar');
    nav.innerHTML = '';
    // رأس القائمة الجانبية مع زر الطي
    const header = el('div', 'sidebar-header');
    header.innerHTML = `<span class="sidebar-header-label">القائمة</span>
      <button class="icon-btn sidebar-toggle-btn" title="طي/فتح القائمة">
        <span class="material-symbols-outlined" style="font-size:20px">menu_open</span>
      </button>`;
    nav.appendChild(header);
    header.querySelector('.sidebar-toggle-btn').onclick = () => {
      nav.classList.toggle('collapsed');
    };
    // عناصر التنقل
    pages.forEach(p => {
      if (!can(p.permission)) return;
      const b = el('button', 'nav-item');
      b.dataset.page = p.key;
      const iconHtml = p.icon ? `<span class="material-symbols-outlined" style="font-size:20px">${p.icon}</span>` : '';
      b.innerHTML = `${iconHtml}<span>${esc(p.title)}</span>`;
      b.onclick = () => { go(p.key); };
      nav.appendChild(b);
    });
  }

  function go(key, params) {
    const id = params && params.id !== undefined ? '/' + params.id : '';
    const target = '#/' + key + id;
    if (location.hash === target) route();
    else location.hash = target;
  }

  async function route() {
    if (!state.ready) return;
    const raw = (location.hash || '').replace(/^#\/?/, '');
    const parts = raw.split('/').filter(Boolean);
    const key = parts[0] || firstAllowedPage();
    const page = pages.get(key);
    const content = document.getElementById('content');

    document.querySelectorAll('.nav-item').forEach(n =>
      n.classList.toggle('active', n.dataset.page === key));

    if (!page) {
      content.innerHTML = '';
      content.appendChild(empty('الصفحة غير موجودة', 'search'));
      return;
    }
    if (!can(page.permission)) {
      content.innerHTML = '';
      content.appendChild(empty('لا تملك صلاحية الوصول إلى هذه الشاشة', 'lock'));
      return;
    }

    const params = { id: parts[1] ? parts[1] : null, rest: parts.slice(2) };
    const stop = spinner(content);
    try {
      content.innerHTML = '';
      await page.render(content, params);
    } catch (e) {
      content.innerHTML = '';
      const box = el('div', 'note danger');
      box.textContent = (e && e.message) ? e.message : 'تعذّر عرض الصفحة';
      content.appendChild(box);
    } finally {
      stop();
    }
  }

  function firstAllowedPage() {
    for (const [k, p] of pages) if (can(p.permission)) return k;
    return 'dashboard';
  }

  // ===================== التنبيهات =====================

  async function refreshNotifCount() {
    try {
      const r = await api.get('/api/notifications/count');
      const badgeEl = document.getElementById('notif-count');
      const n = r && r.unread ? r.unread : 0;
      badgeEl.textContent = n > 99 ? '99+' : n;
      badgeEl.hidden = n === 0;
    } catch (e) { /* تجاهل — لا نزعج المستخدم بفشل الاستطلاع */ }
  }

  async function openNotifPanel() {
    const panel = document.getElementById('notif-panel');
    const list = document.getElementById('notif-list');
    panel.hidden = !panel.hidden;
    if (panel.hidden) return;
    list.innerHTML = '<div class="spinner">جارٍ التحميل…</div>';
    try {
      const items = await api.get('/api/notifications', { limit: 25 });
      list.innerHTML = '';
      if (!items.length) { list.appendChild(empty('لا توجد تنبيهات', 'bell')); return; }
      items.forEach(n => {
        const d = el('div', 'notif-item' + (n.read ? '' : ' unread'));
        d.innerHTML = `<div class="notif-title">${esc(n.title)}</div>
          <div class="notif-msg">${esc(n.message || '')}</div>
          <div class="notif-time">${fmt.dateTime(n.createdAt)}</div>`;
        d.onclick = async () => {
          if (!n.read) { try { await api.post(`/api/notifications/${n.id}/read`); } catch (e) {} }
          panel.hidden = true;
          refreshNotifCount();
          if (n.linkType && n.linkId) go(pageOfLinkType(n.linkType), { id: n.linkId });
        };
        list.appendChild(d);
      });
    } catch (e) {
      list.innerHTML = '';
      list.appendChild(empty('تعذّر تحميل التنبيهات', 'alert'));
    }
  }

  const pageOfLinkType = (t) => ({
    FINANCIAL_FILE: 'financial', CASE: 'cases', EXECUTION: 'execution', CONSULTATION: 'consultations'
  }[t] || 'dashboard');

  // ===================== الدخول والخروج =====================

  function showLogin(errorMsg) {
    state.ready = false;
    if (notifTimer) { clearInterval(notifTimer); notifTimer = null; }
    document.getElementById('app-shell').hidden = true;
    document.getElementById('login-screen').hidden = false;
    const box = document.getElementById('login-error');
    if (errorMsg) { box.textContent = errorMsg; box.hidden = false; }
    else { box.hidden = true; }
  }

  async function loadSession() {
    const me = await api.get('/api/auth/me');
    state.user = me.user;
    state.permissions = me.permissions || [];
    state.settings = me.settings || {};
    try { state.lookups = await api.get('/api/lookups'); } catch (e) { state.lookups = {}; }

    document.getElementById('login-screen').hidden = true;
    document.getElementById('app-shell').hidden = false;
    document.getElementById('user-name').textContent = state.user.fullName || state.user.username;
    document.getElementById('user-role').textContent =
      (state.user.role && state.user.role.nameAr) ? state.user.role.nameAr : '';
    document.getElementById('user-initial').textContent =
      (state.user.fullName || 'م').trim().charAt(0);
    if (state.settings.officeName) {
      document.getElementById('office-name').textContent = state.settings.officeName;
    }

    state.ready = true;
    buildSidebar();
    route();
    refreshNotifCount();
    notifTimer = setInterval(refreshNotifCount, 60000);

    if (state.user.mustChangePassword) {
      openChangePassword(true);
    }
  }

  function openChangePassword(forced) {
    const f = form([
      { key: 'oldPassword', label: 'كلمة المرور الحالية', type: 'text', required: true, col: 2 },
      { key: 'newPassword', label: 'كلمة المرور الجديدة', type: 'text', required: true, col: 2,
        hint: 'ثمانية أحرف على الأقل، وتشمل حرفاً كبيراً وحرفاً صغيراً ورقماً ورمزاً خاصاً' },
      { key: 'confirm', label: 'تأكيد كلمة المرور الجديدة', type: 'text', required: true, col: 2 }
    ], {});
    // إخفاء النص أثناء الكتابة
    ['oldPassword', 'newPassword', 'confirm'].forEach(k => { f.inputs[k].type = 'password'; });

    const body = el('div');
    if (forced) {
      body.appendChild(el('div', 'note warn', 'يجب تغيير كلمة المرور قبل متابعة استخدام النظام.'));
    }
    body.appendChild(f.node);

    modal({
      title: 'تغيير كلمة المرور',
      bodyNode: body,
      actions: [
        { label: 'حفظ', type: 'gold', onClick: async () => {
            const v = f.read();
            if (v.newPassword !== v.confirm) throw { message: 'كلمتا المرور غير متطابقتين', blockers: [] };
            await api.post('/api/auth/change-password',
              { oldPassword: v.oldPassword, newPassword: v.newPassword });
            closeModal();
            toast('تم تغيير كلمة المرور', 'success');
            state.user.mustChangePassword = false;
          } },
        { label: 'إلغاء', onClick: () => closeModal() }
      ]
    });
  }

  // ===================== ربط الأحداث =====================

  function bindShell() {
    document.getElementById('login-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const btn = document.getElementById('login-btn');
      const box = document.getElementById('login-error');
      box.hidden = true;
      btn.disabled = true;
      btn.textContent = 'جارٍ الدخول…';
      try {
        await api.post('/api/auth/login', {
          username: document.getElementById('login-username').value.trim(),
          password: document.getElementById('login-password').value
        });
        document.getElementById('login-password').value = '';
        await loadSession();
      } catch (err) {
        box.textContent = (err && err.message) || 'تعذّر تسجيل الدخول';
        box.hidden = false;
      } finally {
        btn.disabled = false;
        btn.textContent = 'دخول';
      }
    });

    document.getElementById('btn-logout').onclick = async () => {
      try { await api.post('/api/auth/logout'); } catch (e) {}
      showLogin();
      document.getElementById('user-panel').hidden = true;
    };

    document.getElementById('btn-change-password').onclick = () => {
      document.getElementById('user-panel').hidden = true;
      openChangePassword(false);
    };

    document.getElementById('user-btn').onclick = (e) => {
      e.stopPropagation();
      const p = document.getElementById('user-panel');
      p.hidden = !p.hidden;
      document.getElementById('notif-panel').hidden = true;
    };

    document.getElementById('notif-btn').onclick = (e) => {
      e.stopPropagation();
      document.getElementById('user-panel').hidden = true;
      openNotifPanel();
    };

    document.getElementById('notif-read-all').onclick = async (e) => {
      e.stopPropagation();
      try {
        await api.post('/api/notifications/read-all');
        document.getElementById('notif-panel').hidden = true;
        refreshNotifCount();
        toast('تم تعليم كل التنبيهات كمقروءة', 'success');
      } catch (err) { toastError(err); }
    };

    document.getElementById('nav-toggle').onclick = () =>
      document.getElementById('sidebar').classList.toggle('open');

    document.getElementById('modal-close').onclick = closeModal;
    document.getElementById('modal-backdrop').onclick = closeModal;

    document.addEventListener('click', () => {
      document.getElementById('user-panel').hidden = true;
      document.getElementById('notif-panel').hidden = true;
    });
    ['notif-panel', 'user-panel'].forEach(id =>
      document.getElementById(id).addEventListener('click', e => e.stopPropagation()));

    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });
    window.addEventListener('hashchange', route);
  }

  async function start() {
    bindShell();
    try {
      await loadSession();
    } catch (e) {
      showLogin();
    }
  }

  // ===================== الواجهة العامة =====================

  return {
    state, api, can, go, route, registerPage, start,
    toast, toastError, confirm: confirmBox, modal, closeModal,
    form, table, badge, section, spinner, empty, highlight, printHtml,
    fmt, el, esc, icon,
    /** قائمة خيارات من lookups بصيغة موحّدة. */
    options: (name) => (state.lookups && state.lookups[name]) || [],
    people: (name) => ((state.lookups && state.lookups[name]) || [])
      .map(p => ({ value: p.id, label: p.name })),
    labelOf(name, value) {
      const o = ((state.lookups && state.lookups[name]) || []).find(x => x.value === value);
      return o ? o.label : (value || '—');
    }
  };
})();

window.App = App;
