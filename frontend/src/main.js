import { api, clearAuth, loadAuth, login, rolesFromToken } from './api.js';
import { escapeHtml } from './dom.js';
import { closeModal } from './modal.js';
import { renderPlanningTab } from './views/planning.js';
import { renderBacklogTab } from './views/backlog.js';
import { renderSprintsTab } from './views/sprints.js';
import { renderTeamTab } from './views/team.js';

const app = document.getElementById('app');

const TABS = {
  planning: 'Планирование',
  backlog: 'Задачи и эпики',
  sprints: 'Спринты',
  team: 'Команда и отпуска',
};

let state = {
  auth: normalizeAuth(loadAuth()),
  tab: 'planning',
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

/** @type {{ paint?: Function, reload?: Function } | null} */
let activeView = null;

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

function token() {
  return state.auth?.accessToken;
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
  closeModal();
  activeView = null;
  state.board = null;
  state.etag = null;
  state.selectedTaskId = null;
  state.preview = null;
  state.error = null;
  state.refreshing = false;
  state.tab = 'planning';
}

async function enterApp() {
  if (!isAdmin() && state.tab !== 'planning' && state.tab !== 'team') {
    state.tab = 'planning';
  }
  renderApp();
  await refreshBoard().catch(showError);
  mountTab();
  connectStream();
}

function renderGate() {
  stopUpdates();
  closeModal();
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
  const admin = isAdmin();
  const tabs = admin
    ? Object.entries(TABS)
    : [['planning', TABS.planning], ['team', TABS.team]];

  app.innerHTML = `
    <div class="shell">
      <header class="topbar">
        <div>
          <h1 class="brand">Time<span>line</span></h1>
          <p class="meta">${escapeHtml(state.auth.username)}${admin ? ' · ADMIN' : ''}</p>
        </div>
        <div class="actions">
          <span class="chip" id="live-chip">offline</span>
          <button class="btn-ghost" type="button" id="logout">Выйти</button>
        </div>
      </header>

      <nav class="tabs" role="tablist">
        ${tabs.map(([id, label]) => `
          <button type="button" class="tab ${state.tab === id ? 'active' : ''}"
            role="tab" aria-selected="${state.tab === id}" data-tab="${id}">
            ${escapeHtml(label)}
          </button>`).join('')}
      </nav>

      <div id="tab-root" class="tab-root"></div>
      <p class="error" id="app-error" ${state.error ? '' : 'hidden'}>${escapeHtml(state.error || '')}</p>
    </div>
  `;

  document.getElementById('logout').onclick = () => {
    clearAuth();
    state.auth = null;
    resetSessionState();
    renderGate();
  };
  document.querySelectorAll('[data-tab]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const next = btn.dataset.tab;
      if (next === state.tab) {
        return;
      }
      state.tab = next;
      renderApp();
      mountTab();
      paintLiveChip();
    });
  });
  paintLiveChip();
}

function mountTab() {
  const root = document.getElementById('tab-root');
  if (!root) {
    return;
  }
  closeModal();
  activeView = null;

  if (state.tab === 'planning') {
    activeView = renderPlanningTab(root, {
      getBoard: () => state.board,
      getSelectedTaskId: () => state.selectedTaskId,
      setSelectedTaskId: (id) => { state.selectedTaskId = id; },
      getStatusFilter: () => state.statusFilter,
      setStatusFilter: (value) => { state.statusFilter = value; },
      getPreview: () => state.preview,
      setPreview: (value) => { state.preview = value; },
      isAdmin: isAdmin(),
      token: token(),
      refreshBoard,
      showError,
    });
    return;
  }

  if (state.tab === 'backlog' && isAdmin()) {
    activeView = renderBacklogTab(root, {
      token: token(),
      isAdmin: true,
      onChanged: () => refreshBoard({ force: true }).catch(() => {}),
      showError,
    });
    return;
  }

  if (state.tab === 'sprints' && isAdmin()) {
    activeView = renderSprintsTab(root, {
      token: token(),
      isAdmin: true,
      onChanged: () => refreshBoard({ force: true }).catch(() => {}),
      showError,
    });
    return;
  }

  if (state.tab === 'team') {
    activeView = renderTeamTab(root, {
      token: token(),
      isAdmin: isAdmin(),
      onChanged: () => refreshBoard({ force: true }).catch(() => {}),
      showError,
    });
    return;
  }

  state.tab = 'planning';
  renderApp();
  mountTab();
}

async function refreshBoard({ force = false } = {}) {
  if (!token()) {
    return;
  }
  if (state.refreshing) {
    return;
  }
  state.refreshing = true;
  try {
    const res = await api('/api/v1/timeline/board', {
      token: token(),
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
    if (state.tab === 'planning') {
      activeView?.paint?.();
      document.getElementById('board-root')?.classList.add('flash');
      setTimeout(() => document.getElementById('board-root')?.classList.remove('flash'), 800);
    }
  }
  finally {
    state.refreshing = false;
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
      Authorization: `Bearer ${token()}`,
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
    setTimeout(() => {
      if (generation === streamGeneration && state.auth) {
        connectStream();
      }
    }, 2000);
  }
}

function isBoardUpdatedFrame(frame) {
  let eventName = 'message';
  for (const line of frame.split('\n')) {
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
