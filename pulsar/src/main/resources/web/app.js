// Pulsar Tax — фронтенд без сборки и зависимостей.

const app = document.getElementById('app');
const yearSelect = document.getElementById('year');
const toastEl = document.getElementById('toast');

const REGIMES = {
  MICRO: 'Микробизнес',
  TURNOVER: 'Налог с оборота',
  GENERAL: 'Общая система',
};
const ACTIVITIES = {
  SERVICES: 'Услуги',
  TRADE: 'Торговля',
  HIGH_TECH: 'High-Tech, реестр',
  CONSULTING: 'Консультации',
  ENGINEERING: 'Инжиниринг',
  MARKETING: 'Реклама и маркетинг',
  DESIGN: 'Дизайн',
  TRANSLATION: 'Перевод',
  LEGAL: 'Юридические услуги',
  ACCOUNTING: 'Бухгалтерия и аудит',
  AGENCY: 'Агентская деятельность',
  CUSTOM: 'Своя ставка',
};

const state = { year: new Date().getFullYear() };

// ---------- Утилиты ----------

const money = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });
const fmt = (v) => (v === null || v === undefined ? '—' : `${money.format(Number(v))} ֏`);
const fmtDate = (s) => {
  if (!s) return '';
  const [y, m, d] = s.split('-');
  return `${d}.${m}.${y}`;
};
const today = () => new Date().toISOString().slice(0, 10);

/** Экранирует текст перед вставкой в HTML. */
function esc(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Ошибка ${res.status}`);
  return data;
}

let toastTimer;
function toast(message, isError = false) {
  toastEl.textContent = message;
  toastEl.classList.toggle('error', isError);
  toastEl.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toastEl.hidden = true), 3500);
}

/** Выполняет действие и показывает ошибку, если оно не удалось. */
async function guarded(fn) {
  try {
    await fn();
  } catch (e) {
    toast(e.message, true);
  }
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

// ---------- Навигация ----------

const VIEWS = { overview, incomes, invoices, settings, invoice: invoiceView };

function currentRoute() {
  const [view, id] = location.hash.replace(/^#\/?/, '').split('/');
  return { view: VIEWS[view] ? view : 'overview', id };
}

async function render() {
  const { view, id } = currentRoute();
  document.querySelectorAll('.tab').forEach((t) => {
    const active = t.dataset.view === view || (view === 'invoice' && t.dataset.view === 'invoices');
    t.setAttribute('aria-selected', active);
  });
  app.innerHTML = '<p class="lead">Загрузка…</p>';
  await guarded(() => VIEWS[view](id));
}

document.querySelectorAll('.tab').forEach((t) =>
  t.addEventListener('click', () => (location.hash = `#/${t.dataset.view}`)),
);
window.addEventListener('hashchange', render);

for (let y = state.year + 1; y >= state.year - 4; y--) {
  yearSelect.add(new Option(y, y, false, y === state.year));
}
yearSelect.addEventListener('change', () => {
  state.year = Number(yearSelect.value);
  render();
});

// ---------- Обзор ----------

async function overview() {
  const [s, profile] = await Promise.all([
    api('GET', `/api/summary?year=${state.year}`),
    api('GET', '/api/profile'),
  ]);

  const meterClass = { WARNING: 'warn', EXCEEDED: 'bad' }[s.thresholdStatus] || '';
  const percent = Math.min(Number(s.thresholdPercent || 0), 100);
  const rateText = s.ratePercent === null ? 'не рассчитывается' : `ставка ${s.ratePercent}%`;

  const needsSetup = !profile.name;

  app.innerHTML = `
    <h1>${needsSetup ? 'Добро пожаловать в Pulsar Tax' : esc(profile.name)}</h1>
    <p class="lead">${esc(REGIMES[s.regime])} · ${esc(rateText)} · ${s.year} год</p>

    ${needsSetup ? `<div class="alert">Начните с <a href="#/settings">настроек</a>: укажите имя, ИНН и налоговый режим.</div>` : ''}
    ${s.warnings.map((w) => `<div class="alert ${s.thresholdStatus === 'EXCEEDED' || !s.regimeAllowed ? 'bad' : ''}">${esc(w)}</div>`).join('')}

    <div class="grid cols-3">
      <div class="card">
        <div class="stat-label">Доход за год</div>
        <div class="stat-value">${fmt(s.totalIncome)}</div>
        <div class="stat-sub">по записанным поступлениям</div>
      </div>
      <div class="card">
        <div class="stat-label">Налог за год</div>
        <div class="stat-value">${fmt(s.totalTax)}</div>
        <div class="stat-sub">${esc(rateText)}</div>
      </div>
      <div class="card">
        <div class="stat-label">До порога режима</div>
        <div class="stat-value">${s.threshold === null ? '—' : fmt(s.remaining)}</div>
        ${s.threshold === null ? '<div class="stat-sub">у общей системы порога нет</div>' : `
          <div class="meter">
            <div class="meter-track"><div class="meter-fill ${meterClass}" style="width:${percent}%"></div></div>
            <div class="meter-legend"><span>${s.thresholdPercent}%</span><span>порог ${fmt(s.threshold)}</span></div>
          </div>`}
      </div>
    </div>

    <div class="grid cols-2" style="margin-top:16px">
      <div class="card">
        <h2>По кварталам</h2>
        <div class="quarters">
          ${s.quarters.map((q) => `
            <div class="quarter">
              <div class="q">${q.quarter} квартал</div>
              <div class="amt">${fmt(q.income)}</div>
              <div class="tax">налог ${fmt(q.tax)}</div>
              ${q.dueDate ? `<div class="tax">до ${fmtDate(q.dueDate)}</div>` : ''}
            </div>`).join('')}
        </div>
      </div>
      <div class="card">
        <h2>Ближайшие сроки</h2>
        ${s.deadlines.length === 0 ? '<p class="empty">Сроков на этот год не осталось</p>' : `
          <ul class="deadlines">
            ${s.deadlines.map((d) => `
              <li><span class="date">${d.date ? fmtDate(d.date) : '—'}</span><span>${esc(d.title)}</span></li>`).join('')}
          </ul>`}
      </div>
    </div>`;
}

// ---------- Доходы ----------

async function incomes() {
  const list = await api('GET', `/api/incomes?year=${state.year}`);
  const total = list.reduce((sum, i) => sum + Number(i.amount), 0);

  app.innerHTML = `
    <h1>Доходы</h1>
    <p class="lead">Записывайте каждое поступление денег: от них считается налог и порог режима.</p>

    <div class="card">
      <form id="income-form">
        <div class="row">
          <label class="field">Дата<input name="date" type="date" value="${today()}" required></label>
          <label class="field">Сумма, ֏<input name="amount" type="number" min="1" step="0.01" required placeholder="150000"></label>
          <label class="field">Клиент<input name="client" maxlength="200" placeholder="ООО «Пример»"></label>
          <label class="field">Описание<input name="description" maxlength="500" placeholder="Оплата за сентябрь"></label>
        </div>
        <div class="actions"><button class="btn" type="submit">Добавить доход</button></div>
      </form>
    </div>

    <div class="card" style="margin-top:16px">
      <h2>${state.year} год · итого ${fmt(total)}</h2>
      ${list.length === 0 ? '<p class="empty">Пока нет записей</p>' : `
        <table>
          <thead><tr><th>Дата</th><th>Клиент</th><th class="hide-sm">Описание</th><th class="num">Сумма</th><th></th></tr></thead>
          <tbody>
            ${list.map((i) => `
              <tr>
                <td>${fmtDate(i.date)}</td>
                <td>${esc(i.client)}</td>
                <td class="hide-sm">${esc(i.description)}</td>
                <td class="num">${fmt(i.amount)}</td>
                <td class="num"><button class="btn danger small" data-delete="${i.id}">Удалить</button></td>
              </tr>`).join('')}
          </tbody>
        </table>`}
    </div>`;

  document.getElementById('income-form').addEventListener('submit', (e) => {
    e.preventDefault();
    guarded(async () => {
      await api('POST', '/api/incomes', formData(e.target));
      toast('Доход добавлен');
      render();
    });
  });

  app.querySelectorAll('[data-delete]').forEach((b) =>
    b.addEventListener('click', () => {
      if (!confirm('Удалить запись о доходе?')) return;
      guarded(async () => {
        await api('DELETE', `/api/incomes/${b.dataset.delete}`);
        toast('Запись удалена');
        render();
      });
    }),
  );
}

// ---------- Счета ----------

async function invoices() {
  const list = await api('GET', '/api/invoices');

  app.innerHTML = `
    <h1>Счета</h1>
    <p class="lead">Выставляйте счета клиентам. Когда клиент оплатит, отметьте счёт, и доход запишется сам.</p>

    <div class="card">
      <h2>Новый счёт</h2>
      <form id="invoice-form">
        <div class="row">
          <label class="field">Клиент<input name="client" required maxlength="200" placeholder="ООО «Пример»"></label>
          <label class="field">ИНН клиента<input name="clientTin" maxlength="20"></label>
          <label class="field">Дата<input name="date" type="date" value="${today()}"></label>
          <label class="field">Оплатить до<input name="dueDate" type="date"></label>
        </div>
        <table class="items-table">
          <thead><tr><th>Услуга или товар</th><th class="num">Кол-во</th><th class="num">Цена, ֏</th><th></th></tr></thead>
          <tbody id="items"></tbody>
        </table>
        <div class="actions" style="justify-content:space-between;margin-top:8px">
          <button class="btn ghost small" type="button" id="add-item">+ Позиция</button>
          <div class="items-total" id="items-total"></div>
        </div>
        <div class="actions"><button class="btn" type="submit">Создать счёт</button></div>
      </form>
    </div>

    <div class="card" style="margin-top:16px">
      <h2>Все счета</h2>
      ${list.length === 0 ? '<p class="empty">Счетов пока нет</p>' : `
        <table>
          <thead><tr><th>Номер</th><th>Дата</th><th>Клиент</th><th class="num">Сумма</th><th>Статус</th><th></th></tr></thead>
          <tbody>
            ${list.map((inv) => `
              <tr>
                <td><a href="#/invoice/${inv.id}">${esc(inv.number)}</a></td>
                <td>${fmtDate(inv.date)}</td>
                <td>${esc(inv.client)}</td>
                <td class="num">${fmt(inv.total)}</td>
                <td>${inv.status === 'PAID' ? '<span class="badge paid">Оплачен</span>' : '<span class="badge sent">Ожидает</span>'}</td>
                <td class="num">${inv.status === 'PAID' ? '' : `<button class="btn small" data-paid="${inv.id}">Оплачен</button>`}</td>
              </tr>`).join('')}
          </tbody>
        </table>`}
    </div>`;

  const itemsBody = document.getElementById('items');
  const totalEl = document.getElementById('items-total');

  function recalc() {
    let total = 0;
    itemsBody.querySelectorAll('tr').forEach((tr) => {
      const q = Number(tr.querySelector('[name=quantity]').value || 0);
      const p = Number(tr.querySelector('[name=price]').value || 0);
      total += q * p;
    });
    totalEl.textContent = `Итого: ${fmt(total)}`;
  }

  function addItem() {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><input name="name" required maxlength="300" placeholder="Разработка сайта"></td>
      <td class="num" style="width:110px"><input name="quantity" type="number" min="0.001" step="any" value="1" required></td>
      <td class="num" style="width:160px"><input name="price" type="number" min="0" step="any" required></td>
      <td class="num" style="width:44px"><button class="btn danger small" type="button" aria-label="Удалить позицию">×</button></td>`;
    tr.querySelector('button').addEventListener('click', () => {
      if (itemsBody.children.length > 1) tr.remove();
      recalc();
    });
    tr.querySelectorAll('input').forEach((i) => i.addEventListener('input', recalc));
    itemsBody.appendChild(tr);
    recalc();
  }

  addItem();
  document.getElementById('add-item').addEventListener('click', addItem);

  document.getElementById('invoice-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const form = e.target;
    const body = {
      client: form.client.value,
      clientTin: form.clientTin.value,
      date: form.date.value || null,
      dueDate: form.dueDate.value || null,
      items: [...itemsBody.querySelectorAll('tr')].map((tr) => ({
        name: tr.querySelector('[name=name]').value,
        quantity: tr.querySelector('[name=quantity]').value,
        price: tr.querySelector('[name=price]').value,
      })),
    };
    guarded(async () => {
      const inv = await api('POST', '/api/invoices', body);
      toast(`Счёт ${inv.number} создан`);
      location.hash = `#/invoice/${inv.id}`;
    });
  });

  app.querySelectorAll('[data-paid]').forEach((b) =>
    b.addEventListener('click', () => markPaid(b.dataset.paid)),
  );
}

function markPaid(id) {
  const date = prompt('Дата оплаты (ГГГГ-ММ-ДД)', today());
  if (!date) return;
  guarded(async () => {
    await api('POST', `/api/invoices/${id}/paid?date=${encodeURIComponent(date)}`);
    toast('Счёт оплачен, доход записан');
    render();
  });
}

async function invoiceView(id) {
  const [inv, profile] = await Promise.all([api('GET', `/api/invoices/${id}`), api('GET', '/api/profile')]);

  app.innerHTML = `
    <div class="actions no-print" style="justify-content:space-between;margin-bottom:16px">
      <a class="btn ghost small" href="#/invoices">← Все счета</a>
      <div class="actions">
        ${inv.status === 'PAID' ? '' : '<button class="btn ghost small" id="paid">Отметить оплату</button>'}
        <button class="btn danger small" id="delete">Удалить</button>
        <button class="btn small" id="print">Печать / PDF</button>
      </div>
    </div>

    <div class="card invoice-sheet">
      <div class="head">
        <div>
          <h1>Счёт № ${esc(inv.number)}</h1>
          <div class="stat-sub">от ${fmtDate(inv.date)}${inv.dueDate ? ` · оплатить до ${fmtDate(inv.dueDate)}` : ''}</div>
        </div>
        <div>${inv.status === 'PAID' ? '<span class="badge paid">Оплачен</span>' : '<span class="badge sent">Ожидает оплаты</span>'}</div>
      </div>

      <div class="parties">
        <div>
          <div class="label">Исполнитель</div>
          <div><b>${esc(profile.name) || 'Укажите имя в настройках'}</b></div>
          ${profile.tin ? `<div>ИНН ${esc(profile.tin)}</div>` : ''}
          ${profile.address ? `<div>${esc(profile.address)}</div>` : ''}
          ${profile.bankAccount ? `<div>Счёт ${esc(profile.bankAccount)}</div>` : ''}
        </div>
        <div>
          <div class="label">Заказчик</div>
          <div><b>${esc(inv.client)}</b></div>
          ${inv.clientTin ? `<div>ИНН ${esc(inv.clientTin)}</div>` : ''}
        </div>
      </div>

      <table>
        <thead><tr><th>№</th><th>Наименование</th><th class="num">Кол-во</th><th class="num">Цена</th><th class="num">Сумма</th></tr></thead>
        <tbody>
          ${inv.items.map((it, n) => `
            <tr>
              <td>${n + 1}</td>
              <td>${esc(it.name)}</td>
              <td class="num">${money.format(Number(it.quantity))}</td>
              <td class="num">${fmt(it.price)}</td>
              <td class="num">${fmt(it.sum)}</td>
            </tr>`).join('')}
        </tbody>
      </table>
      <div class="total">Итого к оплате: ${fmt(inv.total)}</div>

      <div class="sign"><span>Исполнитель ____________________</span><span>Заказчик ____________________</span></div>
    </div>`;

  document.getElementById('print').addEventListener('click', () => window.print());
  document.getElementById('paid')?.addEventListener('click', () => markPaid(inv.id));
  document.getElementById('delete').addEventListener('click', () => {
    if (!confirm(`Удалить счёт ${inv.number}? Доход по его оплате тоже удалится.`)) return;
    guarded(async () => {
      await api('DELETE', `/api/invoices/${inv.id}`);
      toast('Счёт удалён');
      location.hash = '#/invoices';
    });
  });
}

// ---------- Настройки ----------

async function settings() {
  const [p, rules] = await Promise.all([
    api('GET', '/api/profile'),
    api('GET', `/api/rules?year=${state.year}`),
  ]);
  const byActivity = Object.fromEntries(rules.activities.map((a) => [a.activity, a]));
  const activityLabels = Object.fromEntries(Object.entries(ACTIVITIES).map(([k, v]) => {
    const rate = byActivity[k]?.turnoverRate;
    return [k, rate === null || rate === undefined ? v : `${v} (${rate}%)`];
  }));
  const options = (map, selected) =>
    Object.entries(map).map(([k, v]) => `<option value="${k}" ${k === selected ? 'selected' : ''}>${esc(v)}</option>`).join('');

  app.innerHTML = `
    <h1>Настройки</h1>
    <p class="lead">Эти данные попадают в счета и определяют расчёт налога.</p>

    <form id="profile-form" class="card">
      <h2>Предприниматель</h2>
      <div class="row">
        <label class="field">Имя или название ИП<input name="name" maxlength="200" value="${esc(p.name)}"></label>
        <label class="field">ИНН<input name="tin" maxlength="20" value="${esc(p.tin)}"></label>
      </div>
      <div class="row">
        <label class="field">Адрес<input name="address" maxlength="300" value="${esc(p.address)}"></label>
        <label class="field">Банковский счёт<input name="bankAccount" maxlength="50" value="${esc(p.bankAccount)}"></label>
      </div>

      <h2 style="margin-top:18px">Налоговый режим</h2>
      <div class="row">
        <label class="field">Режим<select name="regime">${options(REGIMES, p.regime)}</select></label>
        <label class="field">Вид деятельности<select name="activity">${options(activityLabels, p.activity)}</select></label>
        <label class="field" id="rate-field">Своя ставка, %<input name="customRate" type="number" min="0" max="100" step="0.1" value="${p.customRate ?? ''}"></label>
      </div>
      <div class="alert" id="regime-check" hidden></div>
      <p class="hint">
        Микробизнес — оборот до ${money.format(rules.microThreshold / 1e6)} млн ֏ в год, налог с дохода не платится.
        Налог с оборота — до ${money.format(rules.turnoverThreshold / 1e6)} млн ֏, ставка зависит от деятельности.
        Режим выбирается заявлением до 20 февраля, а при регистрации ИП — в течение 20 дней.
        Правила ${rules.rulesFromYear} года; сверьте их с бухгалтером или на taxservice.am.
      </p>
      <div class="actions"><button class="btn" type="submit">Сохранить</button></div>
    </form>`;

  const form = document.getElementById('profile-form');
  const rateField = document.getElementById('rate-field');
  const regimeCheck = document.getElementById('regime-check');
  const syncRate = () => {
    rateField.hidden = !(form.regime.value === 'TURNOVER' && form.activity.value === 'CUSTOM');
    const allowed = byActivity[form.activity.value]?.allowedRegimes ?? [];
    const ok = allowed.includes(form.regime.value);
    regimeCheck.hidden = ok;
    regimeCheck.classList.toggle('bad', !ok);
    regimeCheck.textContent = ok ? '' : `«${ACTIVITIES[form.activity.value]}» не может применять режим «${REGIMES[form.regime.value]}». `
      + `Доступно: ${allowed.map((r) => REGIMES[r]).join(', ')}.`;
  };
  form.regime.addEventListener('change', syncRate);
  form.activity.addEventListener('change', syncRate);
  syncRate();

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const f = form.elements;
    const body = {
      name: f.namedItem('name').value,
      tin: f.namedItem('tin').value,
      address: f.namedItem('address').value,
      bankAccount: f.namedItem('bankAccount').value,
      regime: f.namedItem('regime').value,
      activity: f.namedItem('activity').value,
      customRate: rateField.hidden ? null : f.namedItem('customRate').value || null,
    };
    guarded(async () => {
      await api('PUT', '/api/profile', body);
      toast('Настройки сохранены');
      location.hash = '#/overview';
    });
  });
}

render();
