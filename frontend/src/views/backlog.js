import { api } from '../api.js';
import { escapeHtml, ensureOk } from '../dom.js';
import { openModal } from '../modal.js';

export async function loadBacklogData(token) {
  const [epicsRes, tasksRes] = await Promise.all([
    api('/api/v1/epics', { token }),
    api('/api/v1/tasks', { token }),
  ]);
  await ensureOk(epicsRes, 'Не удалось загрузить эпики');
  await ensureOk(tasksRes, 'Не удалось загрузить задачи');
  return {
    epics: await epicsRes.json(),
    tasks: await tasksRes.json(),
  };
}

export function renderBacklogTab(root, ctx) {
  const { token, isAdmin, onChanged, showError } = ctx;
  let cache = { epics: [], tasks: [] };

  async function reload() {
    root.innerHTML = '<p class="meta">Загрузка бэклога…</p>';
    try {
      cache = await loadBacklogData(token);
      paint();
    }
    catch (err) {
      root.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
      showError?.(err);
    }
  }

  function paint() {
    const epicById = Object.fromEntries(cache.epics.map((epic) => [epic.id, epic]));
    root.innerHTML = `
      <div class="admin-toolbar">
        <div>
          <h2 class="section-title">Эпики и задачи</h2>
          <p class="hint">Создание и правка в модальных окнах.</p>
        </div>
        <div class="actions">
          ${isAdmin ? `
            <button type="button" class="btn-ghost" id="btn-epic">Новый эпик</button>
            <button type="button" class="btn-primary" id="btn-task">Новая задача</button>
          ` : ''}
          <button type="button" class="btn-ghost" id="btn-reload-backlog">Обновить</button>
        </div>
      </div>

      <h3 class="subsection">Эпики</h3>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>Ключ</th><th>Название</th><th>Порядок</th><th></th></tr>
          </thead>
          <tbody>
            ${cache.epics.length ? cache.epics.map((epic) => `
              <tr>
                <td class="mono">${escapeHtml(epic.key)}</td>
                <td>${escapeHtml(epic.name)}</td>
                <td>${epic.orderIndex}</td>
                <td class="row-actions">
                  ${isAdmin ? `<button type="button" class="linkish" data-edit-epic="${epic.id}">Изменить</button>` : '—'}
                </td>
              </tr>`).join('') : '<tr><td colspan="4" class="cell-empty">Эпиков пока нет</td></tr>'}
          </tbody>
        </table>
      </div>

      <h3 class="subsection">Задачи</h3>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>Ключ</th><th>Название</th><th>Эпик</th><th>Статус</th><th>Приоритет</th><th></th></tr>
          </thead>
          <tbody>
            ${cache.tasks.length ? cache.tasks.map((task) => {
              const epic = task.epicId ? epicById[task.epicId] : null;
              return `
              <tr>
                <td class="mono">${escapeHtml(task.key)}</td>
                <td>${escapeHtml(task.title)}</td>
                <td>${epic ? escapeHtml(epic.key) : '—'}</td>
                <td><span class="status">${escapeHtml(task.status)}</span></td>
                <td>${task.priority}</td>
                <td class="row-actions">
                  ${isAdmin ? `
                    <button type="button" class="linkish" data-edit-task="${task.id}">Изменить</button>
                    <button type="button" class="linkish danger" data-delete-task="${task.id}">Удалить</button>
                  ` : '—'}
                </td>
              </tr>`;
            }).join('') : '<tr><td colspan="6" class="cell-empty">Задач пока нет</td></tr>'}
          </tbody>
        </table>
      </div>
    `;

    root.querySelector('#btn-reload-backlog')?.addEventListener('click', () => reload());
    root.querySelector('#btn-epic')?.addEventListener('click', () => openEpicModal());
    root.querySelector('#btn-task')?.addEventListener('click', () => openTaskModal());
    root.querySelectorAll('[data-edit-epic]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const epic = cache.epics.find((item) => item.id === btn.dataset.editEpic);
        openEpicModal(epic);
      });
    });
    root.querySelectorAll('[data-edit-task]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const task = cache.tasks.find((item) => item.id === btn.dataset.editTask);
        openTaskModal(task);
      });
    });
    root.querySelectorAll('[data-delete-task]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const task = cache.tasks.find((item) => item.id === btn.dataset.deleteTask);
        if (!task || !confirm(`Удалить задачу ${task.key}?`)) {
          return;
        }
        try {
          const res = await api(`/api/v1/tasks/${task.id}`, { method: 'DELETE', token });
          await ensureOk(res, 'Не удалось удалить задачу');
          await reload();
          onChanged?.();
        }
        catch (err) {
          showError?.(err);
        }
      });
    });
  }

  function openEpicModal(epic) {
    const editing = Boolean(epic);
    const modal = openModal({
      title: editing ? `Эпик ${epic.key}` : 'Новый эпик',
      bodyHtml: `
        <form id="epic-form" class="modal-form">
          ${editing ? '' : `
            <div class="field">
              <label for="epic-key">Ключ</label>
              <input id="epic-key" name="key" required maxlength="32" placeholder="AUTH" />
            </div>`}
          <div class="field">
            <label for="epic-name">Название</label>
            <input id="epic-name" name="name" required value="${escapeHtml(epic?.name || '')}" />
          </div>
          <div class="field">
            <label for="epic-color">Цвет</label>
            <input id="epic-color" name="color" type="color" value="${escapeHtml(epic?.color || '#0f7a6c')}" />
          </div>
          <div class="field">
            <label for="epic-order">Порядок</label>
            <input id="epic-order" name="orderIndex" type="number" value="${epic?.orderIndex ?? cache.epics.length + 1}" />
          </div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </form>
      `,
    });

    modal.body.querySelector('#epic-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      try {
        if (editing) {
          const res = await api(`/api/v1/epics/${epic.id}`, {
            method: 'PUT',
            token,
            body: {
              name: form.name.value.trim(),
              color: form.color.value,
              orderIndex: Number(form.orderIndex.value || 0),
            },
          });
          await ensureOk(res, 'Не удалось обновить эпик');
        }
        else {
          const res = await api('/api/v1/epics', {
            method: 'POST',
            token,
            body: {
              key: form.key.value.trim(),
              name: form.name.value.trim(),
              color: form.color.value,
              orderIndex: Number(form.orderIndex.value || 0),
            },
          });
          await ensureOk(res, 'Не удалось создать эпик');
        }
        modal.close();
        await reload();
        onChanged?.();
      }
      catch (err) {
        modal.setError(err.message);
      }
    });
  }

  function openTaskModal(task) {
    const editing = Boolean(task);
    const modal = openModal({
      title: editing ? `Задача ${task.key}` : 'Новая задача',
      bodyHtml: `
        <form id="task-form" class="modal-form">
          ${editing ? '' : `
            <div class="field">
              <label for="task-key">Ключ</label>
              <input id="task-key" name="key" required maxlength="32" placeholder="T-10" />
            </div>`}
          <div class="field">
            <label for="task-title">Название</label>
            <input id="task-title" name="title" required maxlength="512" value="${escapeHtml(task?.title || '')}" />
          </div>
          <div class="field">
            <label for="task-epic">Эпик</label>
            <select id="task-epic" name="epicId">
              <option value="">Без эпика</option>
              ${cache.epics.map((epic) => `
                <option value="${epic.id}" ${task?.epicId === epic.id ? 'selected' : ''}>
                  ${escapeHtml(epic.key)} · ${escapeHtml(epic.name)}
                </option>`).join('')}
            </select>
          </div>
          <div class="field">
            <label for="task-priority">Приоритет</label>
            <input id="task-priority" name="priority" type="number" value="${task?.priority ?? 0}" />
          </div>
          ${editing ? `
            <div class="field">
              <label for="task-status">Статус</label>
              <select id="task-status" name="status">
                ${['TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED'].map((status) =>
                  `<option value="${status}" ${task.status === status ? 'selected' : ''}>${status}</option>`).join('')}
              </select>
            </div>` : ''}
          <div class="field field-wide">
            <label for="task-description">Описание</label>
            <textarea id="task-description" name="description" rows="3">${escapeHtml(task?.description || '')}</textarea>
          </div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </form>
      `,
    });

    modal.body.querySelector('#task-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      try {
        if (editing) {
          const updateRes = await api(`/api/v1/tasks/${task.id}`, {
            method: 'PUT',
            token,
            body: {
              title: form.title.value.trim(),
              epicId: form.epicId.value || null,
              priority: Number(form.priority.value || 0),
              description: form.description.value.trim() || null,
            },
          });
          await ensureOk(updateRes, 'Не удалось обновить задачу');
          if (form.status && form.status.value !== task.status) {
            const statusRes = await api(`/api/v1/tasks/${task.id}/status`, {
              method: 'PUT',
              token,
              body: { status: form.status.value },
            });
            await ensureOk(statusRes, 'Не удалось сменить статус');
          }
        }
        else {
          const res = await api('/api/v1/tasks', {
            method: 'POST',
            token,
            body: {
              key: form.key.value.trim(),
              title: form.title.value.trim(),
              epicId: form.epicId.value || null,
              priority: Number(form.priority.value || 0),
              description: form.description.value.trim() || null,
            },
          });
          await ensureOk(res, 'Не удалось создать задачу');
        }
        modal.close();
        await reload();
        onChanged?.();
      }
      catch (err) {
        modal.setError(err.message);
      }
    });
  }

  reload();
  return { reload };
}
