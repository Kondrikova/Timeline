import { api, clearAuth, loadAuth, login } from './api.js';

const app = document.getElementById('app');
let state = {
  auth: loadAuth(),
  board: null,
  etag: null,
  statusFilter: 'ALL',
  selectedTaskId: null,
  preview: null,
  error: null,
  live: false,
};

let eventSource = null;

boot();

function boot() {
  if (state.auth?.accessToken) {
    renderApp();
    refreshBoard().catch(showError);
    connectStream();
  }
  else {
    renderGate();
  }
}

function renderGate() {
  app.innerHTML = `
    <section class="gate">
      <div class="gate-card">
        <h1 class="brand">Time<span>line</span></h1>
        <p class="lede">План команды на месяцы вперёд: ёмкость ролей, спринты и каскад зависимостей.</p>
        <form id="login-form">
          <div class="field">
            <label for="username">Логин</label>
            <input id="username" name="username" value="anna.admin" autocomplete="username" required />
          </div>
          <div class="field">
            <label for="password">Пароль</label>
            <input id="password" name="password" type="password" value="admin123" autocomplete="current-password" required />
          </div>
          <div class="actions">
            <button class="btn-primary" type="submit">Открыть доску</button>
          </div>
          <p class="error" id="login-error" hidden></p>
        </form>
      </div>
    </section>
  `;
  document.getElementById('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const username = event.target.username.value.trim();
    const password = event.target.password.value;
    const errorEl = document.getElementById('login-error');
    try {
      state.auth = await login(username, password);
      state.error = null;
      renderApp();
      await refreshBoard();
      connectStream();
    }
    catch (err) {
      errorEl.hidden = false;
      errorEl.textContent = err.message;
    }
  });
}

function renderApp() {
  app.innerHTML = `
    <div class="shell">
      <header class="topbar">
        <div>
          <h1 class="brand">Time<span>line</span></h1>
          <p class="meta">Доска плана · ${escapeHtml(state.auth.username)}</p>
        </div>
        <div class="actions">
          <span class="chip ${state.live ? 'live' : ''}" id="live-chip">${state.live ? 'SSE' : 'offline'}</span>
          <button class="btn-ghost" type="button" id="logout">Выйти</button>
        </div>
      </header>
      <div class="toolbar">
        <label for="status-filter">Статус</label>
        <select id="status-filter">
          ${['ALL', 'TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED'].map((status) =>
            `<option value="${status}" ${state.statusFilter === status ? 'selected' : ''}>${status}</option>`).join('')}
        </select>
        <button class="btn-ghost" type="button" id="reload">Обновить</button>
        <span class="chip warn" id="conflicts-chip"></span>
      </div>
      <div class="board-wrap" id="board-root"></div>
      <section class="move-panel" id="move-panel"></section>
      <p class="error" id="app-error" ${state.error ? '' : 'hidden'}>${escapeHtml(state.error || '')}</p>
    </div>
  `;

  document.getElementById('logout').onclick = () => {
    clearAuth();
    eventSource?.close();
    state = { ...state, auth: null, board: null, live: false };
    renderGate();
  };
  document.getElementById('reload').onclick = () => refreshBoard().catch(showError);
  document.getElementById('status-filter').onchange = (event) => {
    state.statusFilter = event.target.value;
    paintBoard();
  };
  paintBoard();
  paintMovePanel();
}

async function refreshBoard() {
  const res = await api('/api/v1/timeline/board', {
    token: state.auth.accessToken,
    etag: state.etag,
  });
  if (res.status === 304) {
    return;
  }
  if (res.status === 401) {
    clearAuth();
    renderGate();
    throw new Error('Сессия истекла');
  }
  if (!res.ok) {
    throw new Error(`Доска недоступна (${res.status})`);
  }
  state.etag = res.headers.get('ETag');
  state.board = await res.json();
  state.error = null;
  paintBoard();
  paintMovePanel();
  document.getElementById('board-root')?.classList.add('flash');
  setTimeout(() => document.getElementById('board-root')?.classList.remove('flash'), 800);
}

function paintBoard() {
  const root = document.getElementById('board-root');
  const conflictsChip = document.getElementById('conflicts-chip');
  if (!root || !state.board) {
    if (root) {
      root.innerHTML = '<p class="meta" style="padding:1rem">Загрузка доски…</p>';
    }
    return;
  }

  const board = state.board;
  const summary = board.conflictsSummary || {};
  const conflictCount = Object.values(summary).reduce((a, b) => a + b, 0);
  if (conflictsChip) {
    conflictsChip.textContent = conflictCount
      ? `конфликты: ${conflictCount}`
      : 'конфликтов нет';
  }

  const sprints = board.sprints || [];
  const epics = board.epics || [];

  const head = `
    <tr>
      <th>Задача</th>
      ${sprints.map((sprint) => `
        <th>
          ${escapeHtml(sprint.name || `S${sprint.number}`)}
          <small>${escapeHtml(sprint.startDate || '')} — ${escapeHtml(sprint.endDate || '')}</small>
          <div class="capacity">${capacityHtml(sprint.capacity || [])}</div>
        </th>`).join('')}
    </tr>`;

  const body = epics.map((epic) => {
    const tasks = (epic.tasks || []).filter((task) =>
      state.statusFilter === 'ALL' || task.status === state.statusFilter);
    const epicRow = `
      <tr class="epic-row">
        <td colspan="${sprints.length + 1}">${escapeHtml(epic.key || '—')} · ${escapeHtml(epic.name || 'Без эпика')}</td>
      </tr>`;
    const taskRows = tasks.map((task) => {
      const selected = state.selectedTaskId === task.id ? 'selected' : '';
      return `
        <tr class="task-row ${selected}" data-task-id="${task.id}">
          <td>
            <span class="task-key">${escapeHtml(task.key)}</span>
            <span class="task-title">${escapeHtml(task.title)}</span>
            <span class="status">${escapeHtml(task.status)}</span>
          </td>
          ${sprints.map((sprint) => cellHtml(task, sprint.id)).join('')}
        </tr>`;
    }).join('');
    return epicRow + taskRows;
  }).join('');

  root.innerHTML = `<table class="board"><thead>${head}</thead><tbody>${body}</tbody></table>`;
  root.querySelectorAll('.task-row').forEach((row) => {
    row.addEventListener('click', () => {
      state.selectedTaskId = row.dataset.taskId;
      paintBoard();
      paintMovePanel();
    });
  });
}

function capacityHtml(cells) {
  if (!cells.length) {
    return 'ёмкость появится после событий планирования';
  }
  return cells.map((cell) => {
    const free = cell.freeSp ?? '—';
    const over = cell.overloaded ? ' over' : '';
    return `<div class="${over}">${escapeHtml(cell.disciplineCode || '?')}: cap ${cell.capacitySp} / alloc ${cell.allocatedSp} / free ${free}</div>`;
  }).join('');
}

function cellHtml(task, sprintId) {
  const cells = (task.cells || []).filter((cell) => cell.sprintId === sprintId);
  if (!cells.length) {
    return '<td class="cell-empty">—</td>';
  }
  return `<td>${cells.map((cell) => {
    const conflict = (cell.conflicts || []).length ? 'conflict' : '';
    return `<div class="cell-sp ${conflict}">${escapeHtml(String(cell.plannedSp))}${conflict ? ' !' : ''}</div>`;
  }).join('')}</td>`;
}

function paintMovePanel() {
  const panel = document.getElementById('move-panel');
  if (!panel || !state.board) {
    return;
  }
  const tasks = (state.board.epics || []).flatMap((epic) => epic.tasks || []);
  const selected = tasks.find((task) => task.id === state.selectedTaskId);
  const sprints = state.board.sprints || [];

  panel.innerHTML = `
    <h2>Перенос задачи</h2>
    <p class="hint">Выберите строку на доске, затем спринт. Preview показывает каскад, Apply применяет.</p>
    <div class="field">
      <label>Задача</label>
      <select id="move-task">
        <option value="">—</option>
        ${tasks.map((task) =>
          `<option value="${task.id}" ${selected?.id === task.id ? 'selected' : ''}>${escapeHtml(task.key)} · ${escapeHtml(task.title)}</option>`).join('')}
      </select>
    </div>
    <div class="field">
      <label>В спринт</label>
      <select id="move-sprint">
        ${sprints.map((sprint) =>
          `<option value="${sprint.id}">${escapeHtml(sprint.name || `S${sprint.number}`)}</option>`).join('')}
      </select>
    </div>
    <div class="actions">
      <button class="btn-ghost" type="button" id="move-preview">Preview</button>
      <button class="btn-primary" type="button" id="move-apply">Apply</button>
    </div>
    <pre class="preview" id="move-result">${escapeHtml(state.preview || 'Результат preview появится здесь')}</pre>
  `;

  document.getElementById('move-task').onchange = (event) => {
    state.selectedTaskId = event.target.value || null;
    paintBoard();
  };
  document.getElementById('move-preview').onclick = () => runMove(true);
  document.getElementById('move-apply').onclick = () => runMove(false);
}

async function runMove(preview) {
  const taskId = document.getElementById('move-task')?.value;
  const toSprintId = document.getElementById('move-sprint')?.value;
  if (!taskId || !toSprintId) {
    showError('Выберите задачу и спринт');
    return;
  }
  const res = await api(`/api/v1/plan/tasks/${taskId}/move`, {
    method: 'POST',
    token: state.auth.accessToken,
    body: { toSprintId, preview },
  });
  if (!res.ok) {
    const text = await res.text();
    showError(`Перенос не выполнен (${res.status}): ${text}`);
    return;
  }
  const body = await res.json();
  state.preview = JSON.stringify(body, null, 2);
  paintMovePanel();
  if (!preview) {
    await refreshBoard();
  }
}

function connectStream() {
  eventSource?.close();
  // EventSource cannot set Authorization; use fetch-stream fallback via cookie is N/A.
  // For demo we poll lightly when SSE auth is unavailable, and still try query-less SSE
  // through same-origin proxy after storing token is impossible on EventSource.
  // Workaround: short poll every 15s + manual reload; attempt SSE without auth fails.
  // Use fetch + ReadableStream for authenticated SSE.
  subscribeSse().catch(() => {
    state.live = false;
    const chip = document.getElementById('live-chip');
    if (chip) {
      chip.textContent = 'poll';
      chip.classList.remove('live');
    }
    setInterval(() => refreshBoard().catch(() => {}), 15000);
  });
}

async function subscribeSse() {
  const res = await fetch(`${import.meta.env.VITE_API_BASE || ''}/api/v1/timeline/stream`, {
    headers: { Authorization: `Bearer ${state.auth.accessToken}`, Accept: 'text/event-stream' },
  });
  if (!res.ok || !res.body) {
    throw new Error('SSE unavailable');
  }
  state.live = true;
  const chip = document.getElementById('live-chip');
  if (chip) {
    chip.textContent = 'SSE';
    chip.classList.add('live');
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    if (buffer.includes('board-updated') || buffer.includes('data:')) {
      buffer = '';
      await refreshBoard().catch(() => {});
    }
  }
}

function showError(err) {
  state.error = typeof err === 'string' ? err : err.message;
  const el = document.getElementById('app-error');
  if (el) {
    el.hidden = false;
    el.textContent = state.error;
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}
