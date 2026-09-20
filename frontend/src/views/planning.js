import { api } from '../api.js';
import { escapeHtml, readError } from '../dom.js';
import { openModal } from '../modal.js';

const ROLE_ORDER = ['BE', 'FE', 'QA', 'SA'];

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
      <p class="hint">Оценки ролей и размещение задачи в спринт — на вкладке «Задачи и эпики». Velocity и отпуска — на «Команда и отпуска».</p>
      <div class="board-wrap" id="board-root"></div>
    `;

    const tableRoot = root.querySelector('#board-root');
    const head = `
      <tr>
        <th>Задача</th>
        ${sprints.map((sprint) => `
          <th>
            <div class="sprint-title">${escapeHtml(sprint.name || `Sprint ${sprint.number}`)}</div>
            <small class="sprint-dates">${escapeHtml(sprint.startDate || '')} — ${escapeHtml(sprint.endDate || '')}</small>
            <div class="capacity">${capacityHtml(sprint)}</div>
          </th>`).join('')}
      </tr>`;

    const body = epics.map((epic) => {
      const filtered = (epic.tasks || []).filter((task) =>
        statusFilter === 'ALL' || task.status === statusFilter);
      const epicRow = `
        <tr class="epic-row">
          <td colspan="${Math.max(sprints.length, 0) + 1}">${escapeHtml(epic.key || '—')} · ${escapeHtml(epic.name || 'Без эпика')}</td>
        </tr>`;
      const taskRows = filtered.map((task) => {
        const selected = selectedTaskId === task.id ? 'selected' : '';
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

    tableRoot.innerHTML = `<table class="board"><thead>${head}</thead><tbody>${body || emptyRow(sprints.length)}</tbody></table>`;

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

  function openMoveModal(tasks, sprints, selectedTaskId, preview) {
    const modal = openModal({
      title: 'Перенос задачи',
      bodyHtml: `
        <form id="move-form" class="modal-form">
          <p class="hint">Preview показывает каскад, Apply применяет перенос.</p>
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
                `<option value="${sprint.id}">${escapeHtml(sprint.name || `S${sprint.number}`)}</option>`).join('')}
            </select>
          </div>
          <pre class="preview" id="move-result">${escapeHtml(preview || 'Результат preview появится здесь')}</pre>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Закрыть</button>
            <button type="button" class="btn-ghost" id="move-preview">Preview</button>
            <button type="submit" class="btn-primary">Apply</button>
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
        setPreview(JSON.stringify(body, null, 2));
        modal.body.querySelector('#move-result').textContent = JSON.stringify(body, null, 2);
        if (!previewMode) {
          modal.close();
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

function emptyRow(sprintCount) {
  return `<tr><td colspan="${sprintCount + 1}" class="cell-empty">Пока нет задач</td></tr>`;
}

function capacityHtml(sprint) {
  const cells = sortCapacity(sprint.capacity || []);
  const workingDays = sprint.workingDays
    ?? inferWorkingDays(sprint.startDate, sprint.endDate);
  const lines = [
    `<div class="capacity-days">раб. дней: ${escapeHtml(String(workingDays))}</div>`,
  ];
  if (!cells.length) {
    lines.push('<div>ёмкость появится после пересчёта плана</div>');
    return lines.join('');
  }
  for (const cell of cells) {
    const free = cell.freeSp ?? '—';
    const over = cell.overloaded ? ' over' : '';
    lines.push(
      `<div class="capacity-role${over}">${escapeHtml(cell.disciplineCode || '?')}: `
      + `cap ${escapeHtml(fmtSp(cell.capacitySp))} / alloc ${escapeHtml(fmtSp(cell.allocatedSp))}`
      + ` / free ${escapeHtml(fmtSp(free))}</div>`,
    );
  }
  return lines.join('');
}

function sortCapacity(cells) {
  return [...cells].sort((a, b) => {
    const ai = ROLE_ORDER.indexOf(a.disciplineCode);
    const bi = ROLE_ORDER.indexOf(b.disciplineCode);
    const av = ai === -1 ? 99 : ai;
    const bv = bi === -1 ? 99 : bi;
    if (av !== bv) {
      return av - bv;
    }
    return String(a.disciplineCode || '').localeCompare(String(b.disciplineCode || ''));
  });
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
