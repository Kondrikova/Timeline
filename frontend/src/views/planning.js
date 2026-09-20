import { api } from '../api.js';
import { escapeHtml, readError } from '../dom.js';
import { openModal } from '../modal.js';

const ROLE_ORDER = ['BE', 'FE', 'QA', 'SA'];

const CONFLICT_LABELS = {
  CAPACITY_OVERFLOW: 'Перегруз ёмкости',
  MISSING_ESTIMATE: 'Нет оценки',
  SEQUENCE_VIOLATION: 'Нарушение последовательности',
  SIMULTANEITY_VIOLATION: 'Нарушение совместности',
  BLOCKED_TASK: 'Заблокированная задача',
  OVER_ALLOCATED_TASK: 'Сверх оценки',
  UNPLANNED_TASK: 'Не в плане',
};

export function renderPlanningTab(root, ctx) {
  const {
    getBoard,
    getSelectedTaskId,
    setSelectedTaskId,
    getStatusFilter,
    setStatusFilter,
    getPreview,
    setPreview,
    isAdmin,
    token,
    refreshBoard,
    showError,
  } = ctx;

  function paint() {
    const board = getBoard();
    const statusFilter = getStatusFilter();
    const selectedTaskId = getSelectedTaskId();
    const preview = getPreview();

    if (!board) {
      root.innerHTML = '<p class="meta">Загрузка доски…</p>';
      return;
    }

    const summary = board.conflictsSummary || {};
    const conflictCount = Object.values(summary).reduce((a, b) => a + b, 0);
    const sprints = board.sprints || [];
    const epics = board.epics || [];
    const tasks = epics.flatMap((epic) => epic.tasks || []);
    const roles = collectRoles(sprints);
    const colCount = 1 + sprints.length * Math.max(roles.length, 1);

    root.innerHTML = `
      <div class="toolbar">
        <label for="status-filter">Статус</label>
        <select id="status-filter">
          ${['ALL', 'TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED'].map((status) =>
            `<option value="${status}" ${statusFilter === status ? 'selected' : ''}>${status}</option>`).join('')}
        </select>
        <button class="btn-ghost" type="button" id="reload-board">Обновить</button>
        ${isAdmin ? `
          <button class="btn-primary" type="button" id="btn-move">Перенести задачу…</button>
        ` : ''}
        <span class="chip warn" id="conflicts-chip">${conflictCount ? `конфликты: ${conflictCount}` : 'конфликтов нет'}</span>
      </div>
      <p class="hint">В каждом спринте — колонки ролей (BE/FE/QA/SA). В ячейке SP аллокации этой роли.</p>
      <div class="board-wrap" id="board-root"></div>
    `;

    const tableRoot = root.querySelector('#board-root');
    const roleCount = Math.max(roles.length, 1);
    const sprintHeaders = sprints.map((sprint) => {
      const workingDays = sprint.workingDays
        ?? inferWorkingDays(sprint.startDate, sprint.endDate);
      return `
        <th class="sprint-group" colspan="${roleCount}">
          <div class="sprint-title">${escapeHtml(sprint.name || `Sprint ${sprint.number}`)}</div>
          <small class="sprint-dates">${escapeHtml(sprint.startDate || '')} — ${escapeHtml(sprint.endDate || '')}</small>
          <div class="capacity-days">раб. дней: ${escapeHtml(String(workingDays))}</div>
        </th>`;
    }).join('');

    const roleHeaders = sprints.map((sprint) => {
      if (!roles.length) {
        return '<th class="role-head sprint-start">—</th>';
      }
      return roles.map((role, index) => {
        const cap = (sprint.capacity || []).find((cell) =>
          cell.disciplineId === role.id || cell.disciplineCode === role.code);
        const startClass = index === 0 ? ' sprint-start' : '';
        return `<th class="role-head${startClass}">${roleCapacityHead(role, cap)}</th>`;
      }).join('');
    }).join('');

    const body = epics.map((epic) => {
      const filtered = (epic.tasks || []).filter((task) =>
        statusFilter === 'ALL' || task.status === statusFilter);
      const epicRow = `
        <tr class="epic-row">
          <td colspan="${colCount}">${escapeHtml(epic.key || '—')} · ${escapeHtml(epic.name || 'Без эпика')}</td>
        </tr>`;
      const taskRows = filtered.map((task) => {
        const selected = selectedTaskId === task.id ? 'selected' : '';
        return `
          <tr class="task-row ${selected}" data-task-id="${task.id}">
            <td class="task-cell">
              <span class="task-key">${escapeHtml(task.key)}</span>
              <span class="task-title">${escapeHtml(task.title)}</span>
              <span class="status">${escapeHtml(task.status)}</span>
            </td>
            ${sprints.map((sprint) => roleCellsHtml(task, sprint, roles)).join('')}
          </tr>`;
      }).join('');
      return epicRow + taskRows;
    }).join('');

    tableRoot.innerHTML = `
      <table class="board board-roles">
        <thead>
          <tr>
            <th class="task-head" rowspan="2">Задача</th>
            ${sprintHeaders || ''}
          </tr>
          <tr class="role-head-row">${roleHeaders || '<th class="role-head">—</th>'}</tr>
        </thead>
        <tbody>${body || emptyRow(colCount)}</tbody>
      </table>`;

    root.querySelector('#status-filter').onchange = (event) => {
      setStatusFilter(event.target.value);
      paint();
    };
    root.querySelector('#reload-board').onclick = () => refreshBoard({ force: true }).catch(showError);
    root.querySelector('#btn-move')?.addEventListener('click', () => openMoveModal(tasks, sprints, selectedTaskId, preview));
    tableRoot.querySelectorAll('.task-row').forEach((row) => {
      row.addEventListener('click', () => {
        setSelectedTaskId(row.dataset.taskId);
        paint();
      });
    });
  }

  function openMoveModal(tasks, sprints, selectedTaskId, lastResult) {
    const lookup = buildLookup(tasks, sprints, getBoard());
    const modal = openModal({
      title: 'Перенос задачи',
      bodyHtml: `
        <form id="move-form" class="modal-form">
          <p class="hint">«Проверить» покажет каскад без изменений. «Перенести» применит сдвиг и обновит доску.</p>
          <div class="field">
            <label for="move-task">Задача</label>
            <select id="move-task" name="taskId" required>
              <option value="">—</option>
              ${tasks.map((task) =>
                `<option value="${task.id}" ${selectedTaskId === task.id ? 'selected' : ''}>${escapeHtml(task.key)} · ${escapeHtml(task.title)}</option>`).join('')}
            </select>
          </div>
          <div class="field">
            <label for="move-sprint">В спринт</label>
            <select id="move-sprint" name="sprintId" required>
              ${sprints.map((sprint) =>
                `<option value="${sprint.id}">${escapeHtml(sprintLabel(sprint))}</option>`).join('')}
            </select>
          </div>
          <div class="move-result" id="move-result">${renderMoveResult(lastResult, lookup)}</div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Закрыть</button>
            <button type="button" class="btn-ghost" id="move-preview">Проверить</button>
            <button type="submit" class="btn-primary">Перенести</button>
          </div>
        </form>
      `,
    });

    async function runMove(previewMode) {
      const form = modal.body.querySelector('#move-form');
      const taskId = form.taskId.value;
      const toSprintId = form.sprintId.value;
      modal.setError('');
      if (!taskId || !toSprintId) {
        modal.setError('Выберите задачу и спринт');
        return;
      }
      try {
        const res = await api(`/api/v1/plan/tasks/${taskId}/move`, {
          method: 'POST',
          token,
          body: { toSprintId, preview: previewMode },
        });
        if (!res.ok) {
          throw new Error(await readError(res));
        }
        const body = await res.json();
        setPreview(body);
        modal.body.querySelector('#move-result').innerHTML = renderMoveResult(body, lookup);
        if (!previewMode) {
          await refreshBoard({ force: true });
        }
      }
      catch (err) {
        modal.setError(err.message);
      }
    }

    modal.body.querySelector('#move-preview').onclick = () => runMove(true);
    modal.body.querySelector('#move-form').addEventListener('submit', (event) => {
      event.preventDefault();
      runMove(false);
    });
  }

  paint();
  return { paint };
}

function buildLookup(tasks, sprints, board) {
  const taskById = Object.fromEntries(tasks.map((task) => [task.id, task]));
  const sprintById = Object.fromEntries(sprints.map((sprint) => [sprint.id, sprint]));
  const disciplineById = {};
  for (const sprint of board?.sprints || []) {
    for (const cell of sprint.capacity || []) {
      if (cell.disciplineId && cell.disciplineCode) {
        disciplineById[cell.disciplineId] = cell.disciplineCode;
      }
    }
  }
  return { taskById, sprintById, disciplineById };
}

function sprintLabel(sprint) {
  if (!sprint) {
    return 'спринт';
  }
  return sprint.name || `Sprint ${sprint.number}`;
}

function renderMoveResult(result, lookup) {
  if (!result || typeof result !== 'object') {
    return '<p class="meta">Здесь появится результат проверки или переноса.</p>';
  }

  const shifts = Object.entries(result.shifts || {});
  const conflicts = result.conflicts || [];
  const mode = result.preview
    ? 'Проверка (изменения не применены)'
    : 'Перенос выполнен';

  const shiftRows = shifts.length
    ? shifts
      .sort((a, b) => Number(b[1]) - Number(a[1]) || String(a[0]).localeCompare(String(b[0])))
      .map(([taskId, delta]) => {
        const task = lookup.taskById[taskId];
        const label = task ? `${task.key} · ${task.title}` : taskId.slice(0, 8);
        return `<li><strong>${escapeHtml(label)}</strong> — ${formatShift(delta)}</li>`;
      }).join('')
    : '<li class="meta">Каскадных сдвигов нет</li>';

  const conflictRows = conflicts.length
    ? conflicts.map((conflict) => {
      const type = CONFLICT_LABELS[conflict.type] || conflict.type || 'Конфликт';
      const bits = [
        `<strong>${escapeHtml(type)}</strong>`,
        conflict.severity ? `<span class="status">${escapeHtml(conflict.severity)}</span>` : '',
      ];
      const where = [];
      if (conflict.taskId) {
        const task = lookup.taskById[conflict.taskId];
        where.push(task ? `задача ${task.key}` : `задача ${conflict.taskId.slice(0, 8)}`);
      }
      if (conflict.sprintId) {
        where.push(sprintLabel(lookup.sprintById[conflict.sprintId]));
      }
      if (conflict.disciplineId) {
        where.push(lookup.disciplineById[conflict.disciplineId] || conflict.disciplineId.slice(0, 8));
      }
      return `<li>
        <div>${bits.filter(Boolean).join(' · ')}</div>
        ${where.length ? `<div class="meta">${escapeHtml(where.join(' · '))}</div>` : ''}
        ${conflict.details ? `<div>${escapeHtml(conflict.details)}</div>` : ''}
      </li>`;
    }).join('')
    : '<li class="meta">Конфликтов нет</li>';

  return `
    <div class="move-summary">
      <div class="move-summary-title">${escapeHtml(mode)}</div>
      <div class="move-block">
        <div class="move-block-title">Каскад</div>
        <ul class="move-list">${shiftRows}</ul>
      </div>
      <div class="move-block">
        <div class="move-block-title">Конфликты</div>
        <ul class="move-list">${conflictRows}</ul>
      </div>
    </div>`;
}

function formatShift(delta) {
  const value = Number(delta);
  if (!value) {
    return 'без сдвига';
  }
  const abs = Math.abs(value);
  const unit = abs === 1 ? 'спринт' : abs < 5 ? 'спринта' : 'спринтов';
  if (value > 0) {
    return `сдвиг вперёд на ${abs} ${unit}`;
  }
  return `сдвиг назад на ${abs} ${unit}`;
}

function emptyRow(colCount) {
  return `<tr><td colspan="${colCount}" class="cell-empty">Пока нет задач</td></tr>`;
}

function collectRoles(sprints) {
  const byId = new Map();
  for (const sprint of sprints) {
    for (const cell of sprint.capacity || []) {
      if (!cell.disciplineId) {
        continue;
      }
      if (!byId.has(cell.disciplineId)) {
        byId.set(cell.disciplineId, {
          id: cell.disciplineId,
          code: cell.disciplineCode || '?',
        });
      }
    }
  }
  const roles = [...byId.values()];
  if (!roles.length) {
    return ROLE_ORDER.map((code) => ({ id: null, code }));
  }
  return roles.sort((a, b) => {
    const ai = ROLE_ORDER.indexOf(a.code);
    const bi = ROLE_ORDER.indexOf(b.code);
    return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi)
      || String(a.code).localeCompare(String(b.code));
  });
}

function roleCapacityHead(role, cap) {
  if (!cap) {
    return `
      <div class="role-code">${escapeHtml(role.code)}</div>
      <div class="role-cap meta">cap —</div>`;
  }
  const over = cap.overloaded ? ' over' : '';
  return `
    <div class="role-code">${escapeHtml(role.code)}</div>
    <div class="role-cap${over}">
      <div>cap ${escapeHtml(fmtSp(cap.capacitySp))}</div>
      <div>alloc ${escapeHtml(fmtSp(cap.allocatedSp))}</div>
      <div>free ${escapeHtml(fmtSp(cap.freeSp))}</div>
    </div>`;
}

function roleCellsHtml(task, sprint, roles) {
  if (!roles.length) {
    return '<td class="cell-empty role-cell">—</td>';
  }
  return roles.map((role, index) => {
    const cell = (task.cells || []).find((item) =>
      item.sprintId === sprint.id && role.id && item.disciplineId === role.id);
    const startClass = index === 0 ? ' sprint-start' : '';
    if (!cell) {
      return `<td class="cell-empty role-cell${startClass}">—</td>`;
    }
    const conflict = (cell.conflicts || []).length ? 'conflict' : '';
    return `<td class="role-cell${startClass}">
      <div class="cell-sp ${conflict}">${escapeHtml(fmtSp(cell.plannedSp))}${conflict ? ' !' : ''}</div>
    </td>`;
  }).join('');
}

function fmtSp(value) {
  if (value == null || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return Number.isInteger(num) ? String(num) : String(num);
}

function inferWorkingDays(start, end) {
  if (!start || !end) {
    return '—';
  }
  const from = new Date(`${start}T00:00:00`);
  const to = new Date(`${end}T00:00:00`);
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime()) || to < from) {
    return '—';
  }
  let days = 0;
  for (let cursor = new Date(from); cursor <= to; cursor.setDate(cursor.getDate() + 1)) {
    const dow = cursor.getDay();
    if (dow !== 0 && dow !== 6) {
      days += 1;
    }
  }
  return days;
}
