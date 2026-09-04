/* =====================================================================
   لوحة المعلومات — أول ما يراه المستخدم: صورة فورية عن عمله.
   ===================================================================== */

(function () {
  const { el, esc, fmt } = App;
  let refreshTimer = null;

  App.registerPage('dashboard', {
    title: 'لوحة المعلومات',
    icon: 'dashboard',
    permission: 'DASHBOARD_VIEW',
    render: async (root) => {
      if (refreshTimer) clearInterval(refreshTimer);
      await draw(root);
      // تحديث هادئ كل دقيقة بلا وميض
      refreshTimer = setInterval(async () => {
        if (location.hash !== '#/dashboard') { clearInterval(refreshTimer); return; }
        try { await draw(root, true); } catch (e) { /* تجاهل */ }
      }, 60000);
    }
  });

  async function draw(root, silent) {
    const d = await App.api.get('/api/dashboard');
    const frag = document.createDocumentFragment();

    // ---------- الترويسة ----------
    const head = el('div', 'page-head');
    const left = el('div');
    left.appendChild(el('h2', null, 'لوحة المعلومات'));
    left.appendChild(el('p', 'page-sub',
      d.officeWide ? 'عرض شامل لكل المكتب' : 'الملفات والأعمال المسندة إليك'));
    head.appendChild(left);
    frag.appendChild(head);

    // ---------- مؤشرات حجم العمل ----------
    const icons = { files: 'work', cases: 'balance', executions: 'gavel', consultations: 'description' };
    const tones = { files: '', cases: 'gold', executions: 'warn', consultations: 'ok' };
    const metrics = el('div', 'metrics');
    (d.workload || []).forEach(m => {
      const c = el('div', 'metric ' + (tones[m.key] || ''));
      c.innerHTML = `<span class="metric-icon"><span class="material-symbols-outlined">${icons[m.key] || 'circle'}</span></span>
        <div class="metric-label">${esc(m.label)}</div>
        <div class="metric-value">${fmt.num(m.value)}</div>
        <div class="metric-hint">${esc(m.hint || '')}</div>`;
      metrics.appendChild(c);
    });
    frag.appendChild(metrics);

    // ---------- المؤشرات المالية ----------
    const f = d.financials || {};
    const fin = el('div', 'grid-3');
    fin.appendChild(moneyCard('إجمالي المطالبات', f.claimed, ''));
    fin.appendChild(moneyCard('المحصّل', f.collected, 'ok'));
    fin.appendChild(moneyCard('المستحقات المتبقية', f.outstanding, 'danger'));
    const rateBox = el('div');
    const rate = Number(f.collectionRate || 0);
    rateBox.innerHTML = `<div class="row between mb-1">
        <strong>نسبة التحصيل</strong><strong class="num">${rate}%</strong></div>
      <div class="progress"><span style="width:${Math.min(rate, 100)}%"></span></div>
      <div class="metric-hint" style="margin-top:6px">
        ${esc(fmt.money(f.collected))} من أصل ${esc(fmt.money(f.claimed))}</div>`;
    frag.appendChild(App.section('المؤشرات المالية', wrapNodes([fin, rateBox])));

    // ---------- الملفات العاجلة ----------
    const urgent = d.urgent || [];
    const urgentBox = el('div');
    if (!urgent.length) {
      urgentBox.appendChild(App.empty('لا توجد أعمال عاجلة خلال الأيام القادمة', 'checkCircle'));
    } else {
      const groups = {
        APPEAL_DEADLINE: { title: 'آجال الطعن', icon: 'hourglass', items: [] },
        HEARING: { title: 'الجلسات القادمة', icon: 'balance', items: [] },
        INSTALLMENT: { title: 'الأقساط المستحقة', icon: 'payments', items: [] },
        CONSULTATION: { title: 'استشارات متأخرة', icon: 'description', items: [] }
      };
      urgent.forEach(u => { if (groups[u.kind]) groups[u.kind].items.push(u); });

      const grid = el('div', 'grid-2');
      Object.values(groups).forEach(g => {
        if (!g.items.length) return;
        const box = el('div', 'card');
        box.appendChild(el('div', 'card-head',
          `<h3 style="display:flex;align-items:center;gap:7px">${App.icon(g.icon, 16)} ${esc(g.title)} <span class="badge muted">${g.items.length}</span></h3>`));
        const body = el('div', 'card-body');
        const list = el('div', 'timeline');
        g.items.slice(0, 6).forEach(u => {
          const li = el('div', 'notif-item');
          const days = u.daysLeft === null || u.daysLeft === undefined ? '' :
            (u.daysLeft < 0 ? `متأخر ${Math.abs(u.daysLeft)} يوماً`
              : u.daysLeft === 0 ? 'اليوم' : `خلال ${u.daysLeft} يوماً`);
          li.innerHTML = `<div class="row between">
              <span class="notif-title">${esc(u.title)}</span>
              ${days ? App.badge(days, u.tone) : ''}
            </div>
            <div class="notif-msg">${esc(u.subtitle || '')}</div>
            <div class="notif-time">${fmt.date(u.date)}</div>`;
          li.onclick = () => {
            const page = { FINANCIAL_FILE: 'financial', CASE: 'cases',
              EXECUTION: 'execution', CONSULTATION: 'consultations' }[u.linkType];
            if (page) App.go(page, { id: u.linkId });
          };
          list.appendChild(li);
        });
        body.appendChild(list);
        box.appendChild(body);
        grid.appendChild(box);
      });
      urgentBox.appendChild(grid);
    }
    frag.appendChild(App.section('يستحق خلال أيام', urgentBox));

    // ---------- الرسوم البيانية ----------
    const charts = el('div', 'grid-2');
    charts.appendChild(App.section('توزيع الملفات حسب النوع', barChart(d.byType || [])));
    charts.appendChild(App.section('توزيع الملفات المالية حسب الحالة', donutChart(d.byStatus || [])));
    frag.appendChild(charts);

    // ---------- أداء الفريق ----------
    if (d.officeWide && (d.team || []).length) {
      frag.appendChild(App.section('أداء الفريق', App.table({
        columns: [
          { key: 'name', label: 'العضو' },
          { key: 'roleName', label: 'الدور' },
          { key: 'files', label: 'عدد الملفات', align: 'num' },
          { key: 'avgDays', label: 'متوسط الإنجاز (يوم)', align: 'num',
            format: v => v === null || v === undefined ? '—' : `<span class="num">${v}</span>` },
          { key: 'activeLoad', label: 'الحمل الحالي', align: 'num' }
        ],
        rows: d.team,
        empty: 'لا توجد بيانات أداء'
      })));
    }

    root.innerHTML = '';
    root.appendChild(frag);
  }

  function wrapNodes(nodes) {
    const w = el('div');
    nodes.forEach(n => { w.appendChild(n); w.appendChild(el('div', 'mb-2')); });
    return w;
  }

  function moneyCard(label, value, tone) {
    const c = el('div', 'metric ' + (tone || ''));
    c.innerHTML = `<div class="metric-label">${esc(label)}</div>
      <div class="metric-value" style="font-size:21px">${esc(fmt.money(value))}</div>`;
    return c;
  }

  // ---------- رسم الأعمدة يدوياً بـ SVG ----------
  function barChart(points) {
    const box = el('div');
    if (!points.length) return App.empty('لا توجد بيانات للرسم', 'bar_chart');
    const max = Math.max(...points.map(p => p.value), 1);
    const W = 460, H = 210, pad = 30;
    const bw = (W - pad * 2) / points.length;

    let svg = `<svg viewBox="0 0 ${W} ${H}" style="width:100%;height:auto" role="img">`;
    svg += `<line x1="${pad}" y1="${H - 26}" x2="${W - pad}" y2="${H - 26}" stroke="#e5e7eb" stroke-width="1"/>`;
    points.forEach((p, i) => {
      const h = Math.round((p.value / max) * (H - 70));
      const x = pad + i * bw + bw * 0.2;
      const w = bw * 0.6;
      const y = H - 26 - h;
      svg += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="4" fill="${p.color || '#0f2c4c'}"/>`;
      svg += `<text x="${x + w / 2}" y="${y - 6}" text-anchor="middle" font-size="12" font-weight="700" fill="#0f2c4c">${p.value}</text>`;
      svg += `<text x="${x + w / 2}" y="${H - 9}" text-anchor="middle" font-size="11.5" fill="#6b7280">${esc(p.label)}</text>`;
    });
    svg += '</svg>';
    box.innerHTML = svg;
    return box;
  }

  // ---------- رسم الحلقة يدوياً بـ SVG ----------
  function donutChart(points) {
    const box = el('div');
    const total = points.reduce((s, p) => s + p.value, 0);
    if (!total) return App.empty('لا توجد بيانات للرسم', 'donut_large');

    const R = 70, r = 44, cx = 90, cy = 90;
    let angle = -Math.PI / 2;
    let svg = `<svg viewBox="0 0 180 180" style="width:180px;height:180px" role="img">`;
    points.forEach(p => {
      if (!p.value) return;
      const slice = (p.value / total) * Math.PI * 2;
      const end = angle + slice;
      const large = slice > Math.PI ? 1 : 0;
      const x1 = cx + R * Math.cos(angle), y1 = cy + R * Math.sin(angle);
      const x2 = cx + R * Math.cos(end), y2 = cy + R * Math.sin(end);
      const x3 = cx + r * Math.cos(end), y3 = cy + r * Math.sin(end);
      const x4 = cx + r * Math.cos(angle), y4 = cy + r * Math.sin(angle);
      svg += `<path d="M${x1} ${y1} A${R} ${R} 0 ${large} 1 ${x2} ${y2} L${x3} ${y3} A${r} ${r} 0 ${large} 0 ${x4} ${y4} Z" fill="${p.color}"/>`;
      angle = end;
    });
    svg += `<text x="${cx}" y="${cy - 2}" text-anchor="middle" font-size="22" font-weight="700" fill="#0f2c4c">${total}</text>`;
    svg += `<text x="${cx}" y="${cy + 16}" text-anchor="middle" font-size="11" fill="#6b7280">ملف</text>`;
    svg += '</svg>';

    const row = el('div', 'row');
    const chart = el('div');
    chart.innerHTML = svg;
    row.appendChild(chart);

    const legend = el('div', 'chart-legend');
    legend.style.flexDirection = 'column';
    points.forEach(p => {
      if (!p.value) return;
      const item = el('div');
      item.innerHTML = `<span class="legend-dot" style="background:${p.color}"></span>${esc(p.label)}
        <strong class="num"> ${p.value}</strong>`;
      legend.appendChild(item);
    });
    row.appendChild(legend);
    box.appendChild(row);
    return box;
  }
})();
