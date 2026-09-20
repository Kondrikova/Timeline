import { api, clearAuth, loadAuth, login, rolesFromToken } from './api.js';

const app = document.getElementById('app');

let state = {
  auth: normalizeAuth(loadAuth()),
  board: null,
  etag: null,
  statusFilter: 'ALL',
  selectedTaskId: null,
  preview: null,
  error: null,
  live: false,
  refreshing: false,
};

/** @type {AbortController | null} */
let streamAbort = null;
/** @type {ReturnType<typeof setInterval> | null} */
let pollTimer = null;
let streamGeneration = 0;

boot();

function normalizeAuth(auth) {
  if (!auth?.accessToken) {
    return null;
  }
  if (!auth.roles) {
    auth.roles = rolesFromToken(auth.accessToken);
  }
  return auth;
}

function isAdmin() {
  return Boolean(state.auth?.roles?.includes('ADMIN'));
}

function boot() {
  if (state.auth?.accessToken) {
    enterApp();
  }
  else {
    renderGate();
  }
}

function stopUpdates() {
  streamGeneration += 1;
  streamAbort?.abort();
  streamAbort = null;
  if (pollTimer != null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  state.live = false;
}

function resetSessionState() {
  stopUpdates();
  state.board = null;
  state.etag = null;
  state.selectedTaskId = null;
  state.preview = null;
  state.error = null;
  state.refreshing = false;
}

async function enterApp() {
  renderApp();
  await refreshBoard().catch(showError);
  connectStream();
}

function renderGate() {
  stopUpdates();
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
      resetSessionState();
      state.auth = await login(username, password);
      await enterApp();
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
          <p class="meta">Доска плана · ${escapeHtml(state.auth.username)}${isAdmin() ? ' · ADMIN' : ''}</p>
        </div>
        <div class="actions">
          <span class="chip" id="live-chip">offline</span>
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
      ${isAdmin() ? `<section class="create-panel" id="create-panel"></section>` : ''}
      <div class="board-wrap" id="board-root"></div>
      <section class="move-panel" id="move-panel"></section>
      <p class="error" id="app-error" ${state.error ? '' : 'hidden'}>${escapeHtml(state.error || '')}</p>
    </div>
  `;

  document.getElementById('logout').onclick = () => {
    clearAuth();
    state.auth = null;
    resetSessionState();
    renderGate();
  };
  document.getElementById('reload').onclick = () => refreshBoard({ force: true }).catch(showError);
  document.getElementById('status-filter').onchange = (event) => {
    state.statusFilter = event.target.value;
    paintBoard();
  };
  paintCreatePanel();
  paintBoard();
  paintMovePanel();
  paintLiveChip();
}

function paintCreatePanel() {
  const panel = document.getElementById('create-panel');
  if (!panel || !isAdmin()) {
    return;
  }
  const epics = (state.board?.epics || []).filter((epic) => epic.id);
  panel.innerHTML = `
    <h2>Новая задача</h2>
    <p class="hint">Доступно роли ADMIN. После создания доска обновится по событию проекции.</p>
    <form id="create-task-form" class="create-grid">
      <div class="field">
        <label for="task-key">Ключ</label>
        <input id="task-key" name="key" required placeholder="T-10" maxlength="32" />
      </div>
      <div class="field">
        <label for="task-title">Название</label>
        <input id="task-title" name="title" required placeholder="Описание работы" maxlength="512" />
      </div>
      <div class="field">
        <label for="task-epic">Эпик</label>
        <select id="task-epic" name="epicId">
          <option value="">Без эпика</option>
          ${epics.map((epic) =>
            `<option value="${epic.id}">${escapeHtml(epic.key)} · ${escapeHtml(epic.name)}</option>`).join('')}
        </select>
      </div>
      <div class="field">
        <label for="task-priority">Приоритет</label>
        <input id="task-priority" name="priority" type="number" value="0" />
      </div>
      <div class="field field-wide">
        <label for="task-description">Описание</label>
        <input id="task-description" name="description" placeholder="Необязательно" />
      </div>
      <div class="actions field-wide">
        <button class="btn-primary" type="submit">Добавить задачу</button>
        <button class="btn-ghost" type="button" id="create-epic-toggle">Создать эпик…</button>
      </div>
    </form>
    <form id="create-epic-form" class="create-grid" hidden>
      <div class="field">
        <label for="epic-key">Ключ эпика</label>
        <input id="epic-key" name="key" required placeholder="AUTH" maxlength="32" />
      </div>
      <div class="field">
        <label for="epic-name">Название</label>
        <input id="epic-name" name="name" required placeholder="Авторизация" />
      </div>
      <div class="actions field-wide">
        <button class="btn-primary" type="submit">Создать эпик</button>
      </div>
    </form>
  `;

  document.getElementById('create-epic-toggle').onclick = () => {
    const form = document.getElementById('create-epic-form');
    form.hidden = !form.hidden;
  };

  document.getElementById('create-task-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const payload = {
      key: form.key.value.trim(),
      title: form.title.value.trim(),
      epicId: form.epicId.value || null,
      priority: Number(form.priority.value || 0),
      description: form.description.value.trim() || null,
    };
    try {
      const res = await api('/api/v1/tasks', {
        method: 'POST',
        token: state.auth.accessToken,
        body: payload,
      });
      if (!res.ok) {
        throw new Error(await readError(res));
      }
      form.reset();
      form.priority.value = '0';
      state.error = null;
      hideError();
      // Проекция придёт по SSE; форсируем чтение на случай задержки.
      setTimeout(() => refreshBoard({ force: true }).catch(showError), 400);
    }
    catch (err) {
      showError(err);
    }
  });

  document.getElementById('create-epic-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    try {
      const res = await api('/api/v1/epics', {
        method: 'POST',
        token: state.auth.accessToken,
        body: {
          key: form.key.value.trim(),
          name: form.name.value.trim(),
          color: '#0f7a6c',
          orderIndex: (state.board?.epics || []).length + 1,
        },
      });
      if (!res.ok) {
        throw new Error(await readError(res));
      }
      form.reset();
      form.hidden = true;
      setTimeout(() => refreshBoard({ force: true }).catch(showError), 400);
    }
    catch (err) {
      showError(err);
    }
  });
}

async function refreshBoard({ force = false } = {}) {
  if (!state.auth?.accessToken) {
    return;
  }
  if (state.refreshing) {
    return;
  }
  state.refreshing = true;
  try {
    const res = await api('/api/v1/timeline/board', {
      token: state.auth.accessToken,
      etag: force ? undefined : state.etag,
    });
    if (res.status === 304) {
      return;
    }
    if (res.status === 401) {
      clearAuth();
      state.auth = null;
      resetSessionState();
      renderGate();
      throw new Error('Сессия истекла');
    }
    if (!res.ok) {
      throw new Error(`Доска недоступна (${res.status})`);
    }
    state.etag = res.headers.get('ETag');
    state.board = await res.json();
    state.error = null;
    hideError();
    paintCreatePanel();
    paintBoard();
    paintMovePanel();
    document.getElementById('board-root')?.classList.add('flash');
    setTimeout(() => document.getElementById('board-root')?.classList.remove('flash'), 800);
  }
  finally {
    state.refreshing = false;
  }
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
        <td colspan="${Math.max(sprints.length, 0) + 1}">${escapeHtml(epic.key || '—')} · ${escapeHtml(epic.name || 'Без эпика')}</td>
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

  root.innerHTML = `<table class="board"><thead>${head}</thead><tbody>${body || emptyBoardRow(sprints.length)}</tbody></table>`;
  root.querySelectorAll('.task-row').forEach((row) => {
    row.addEventListener('click', () => {
      state.selectedTaskId = row.dataset.taskId;
      paintBoard();
      paintMovePanel();
    });
  });
}

function emptyBoardRow(sprintCount) {
  return `<tr><td colspan="${sprintCount + 1}" class="cell-empty">Пока нет задач${isAdmin() ? ' — добавьте первую формой выше' : ''}</td></tr>`;
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
      <button class="btn-ghost" type="button" id="move-preview" ${isAdmin() ? '' : 'disabled'}>Preview</button>
      <button class="btn-primary" type="button" id="move-apply" ${isAdmin() ? '' : 'disabled'}>Apply</button>
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
  if (!isAdmin()) {
    showError('Перенос доступен только ADMIN');
    return;
  }
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
    showError(`Перенос не выполнен (${res.status}): ${await readError(res)}`);
    return;
  }
  const body = await res.json();
  state.preview = JSON.stringify(body, null, 2);
  paintMovePanel();
  if (!preview) {
    await refreshBoard({ force: true });
  }
}

function connectStream() {
  stopUpdates();
  const generation = streamGeneration;
  const controller = new AbortController();
  streamAbort = controller;

  subscribeSse(controller.signal, generation).catch(() => {
    if (generation !== streamGeneration) {
      return;
    }
    state.live = false;
    paintLiveChip();
    if (pollTimer == null) {
      pollTimer = setInterval(() => {
        refreshBoard().catch(() => {});
      }, 15000);
    }
  });
}

async function subscribeSse(signal, generation) {
  const res = await fetch(`${import.meta.env.VITE_API_BASE || ''}/api/v1/timeline/stream`, {
    headers: {
      Authorization: `Bearer ${state.auth.accessToken}`,
      Accept: 'text/event-stream',
    },
    signal,
  });
  if (!res.ok || !res.body) {
    throw new Error('SSE unavailable');
  }
  if (generation !== streamGeneration) {
    return;
  }
  state.live = true;
  paintLiveChip();

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    if (generation !== streamGeneration) {
      return;
    }
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split('\n\n');
    buffer = frames.pop() || '';
    for (const frame of frames) {
      if (isBoardUpdatedFrame(frame)) {
        await refreshBoard().catch(() => {});
      }
    }
  }

  if (generation === streamGeneration && state.auth) {
    // Поток оборвался — один тихий reconnect, без наслоения интервалов.
    setTimeout(() => {
      if (generation === streamGeneration && state.auth) {
        connectStream();
      }
    }, 2000);
  }
}

function isBoardUpdatedFrame(frame) {
  const lines = frame.split('\n');
  let eventName = 'message';
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    }
  }
  return eventName === 'board-updated';
}

function paintLiveChip() {
  const chip = document.getElementById('live-chip');
  if (!chip) {
    return;
  }
  if (state.live) {
    chip.textContent = 'SSE';
    chip.classList.add('live');
  }
  else if (pollTimer != null) {
    chip.textContent = 'poll';
    chip.classList.remove('live');
  }
  else {
    chip.textContent = 'offline';
    chip.classList.remove('live');
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

function hideError() {
  const el = document.getElementById('app-error');
  if (el) {
    el.hidden = true;
    el.textContent = '';
  }
}

async function readError(res) {
  try {
    const body = await res.json();
    return body.detail || body.title || body.code || JSON.stringify(body);
  }
  catch {
    return await res.text();
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}
