import { api } from '../api.js';
import { escapeHtml, ensureOk } from '../dom.js';
import { openModal } from '../modal.js';

export async function loadBacklogData(token) {
  const [epicsRes, tasksRes, disciplinesRes, sprintsRes, allocationsRes] = await Promise.all([
    api('/api/v1/epics', { token }),
    api('/api/v1/tasks', { token }),
    api('/api/v1/disciplines', { token }),
    api('/api/v1/sprints', { token }),
    api('/api/v1/plan/allocations', { token }),
  ]);
  await ensureOk(epicsRes, 'Не удалось загрузить эпики');
  await ensureOk(tasksRes, 'Не удалось загрузить задачи');
  await ensureOk(disciplinesRes, 'Не удалось загрузить роли');
  await ensureOk(sprintsRes, 'Не удалось загрузить спринты');
  await ensureOk(allocationsRes, 'Не удалось загрузить аллокации');
  return {
    epics: await epicsRes.json(),
    tasks: await tasksRes.json(),
    disciplines: await disciplinesRes.json(),
    sprints: await sprintsRes.json(),
    allocations: await allocationsRes.json(),
  };
}

export function renderBacklogTab(root, ctx) {
  const { token, isAdmin, onChanged, showError } = ctx;
  let cache = { epics: [], tasks: [], disciplines: [], sprints: [], allocations: [] };

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

  function estimatesLabel(task) {
    const estimates = task.estimates || {};
    const ids = Object.keys(estimates);
    if (!ids.length) {
      return '<span class="cell-empty">роли не заданы</span>';
    }
    return ids.map((id) => {
      const d = cache.disciplines.find((item) => item.id === id);
      const sp = estimates[id];
      const role = d ? `${d.code} · ${d.name}` : id.slice(0, 8);
      return `<div class="mono">${escapeHtml(role)}: ${sp == null ? '—' : escapeHtml(String(sp))} SP</div>`;
    }).join('');
  }

  function allocationLabel(taskId) {
    const rows = cache.allocations.filter((item) => item.taskId === taskId);
    if (!rows.length) {
      return '<span class="cell-empty">не в плане</span>';
    }
    return rows.map((row) => {
      const sprint = cache.sprints.find((item) => item.id === row.sprintId);
      const d = cache.disciplines.find((item) => item.id === row.disciplineId);
      return `<div class="mono">${escapeHtml(sprint?.name || 'S?')} / ${escapeHtml(d?.code || '?')}: ${escapeHtml(String(row.plannedSp))}</div>`;
    }).join('');
  }

  function paint() {
    const epicById = Object.fromEntries(cache.epics.map((epic) => [epic.id, epic]));
    root.innerHTML = `
      <div class="admin-toolbar">
        <div>
          <h2 class="section-title">Эпики и задачи</h2>
          <p class="hint">Оценки задают роли в задаче. «В спринт» создаёт аллокации плана.</p>
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
            <tr><th>Ключ</th><th>Название</th><th>Эпик</th><th>Роли / оценка</th><th>В спринтах</th><th></th></tr>
          </thead>
          <tbody>
            ${cache.tasks.length ? cache.tasks.map((task) => {
              const epic = task.epicId ? epicById[task.epicId] : null;
              return `
              <tr>
                <td class="mono">${escapeHtml(task.key)}</td>
                <td>
                  ${escapeHtml(task.title)}
                  <div class="status">${escapeHtml(task.status)}</div>
                </td>
                <td>${epic ? escapeHtml(epic.key) : '—'}</td>
                <td>${estimatesLabel(task)}</td>
                <td>${allocationLabel(task.id)}</td>
                <td class="row-actions">
                  ${isAdmin ? `
                    <button type="button" class="linkish" data-edit-task="${task.id}">Изменить</button>
                    <button type="button" class="linkish" data-estimates="${task.id}">Оценки</button>
                    <button type="button" class="linkish" data-allocate="${task.id}">В спринт</button>
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
        openEpicModal(cache.epics.find((item) => item.id === btn.dataset.editEpic));
      });
    });
    root.querySelectorAll('[data-edit-task]').forEach((btn) => {
      btn.addEventListener('click', () => {
        openTaskModal(cache.tasks.find((item) => item.id === btn.dataset.editTask));
      });
    });
    root.querySelectorAll('[data-estimates]').forEach((btn) => {
      btn.addEventListener('click', () => {
        openEstimatesModal(cache.tasks.find((item) => item.id === btn.dataset.estimates));
      });
    });
    root.querySelectorAll('[data-allocate]').forEach((btn) => {
      btn.addEventListener('click', () => {
        openAllocateModal(cache.tasks.find((item) => item.id === btn.dataset.allocate));
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
            <div class="field"><label>Ключ</label><input name="key" required maxlength="32" placeholder="AUTH" /></div>`}
          <div class="field"><label>Название</label><input name="name" required value="${escapeHtml(epic?.name || '')}" /></div>
          <div class="field"><label>Цвет</label><input name="color" type="color" value="${escapeHtml(epic?.color || '#0f7a6c')}" /></div>
          <div class="field"><label>Порядок</label><input name="orderIndex" type="number" value="${epic?.orderIndex ?? cache.epics.length + 1}" /></div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </form>`,
    });
    modal.body.querySelector('#epic-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      try {
        const res = editing
          ? await api(`/api/v1/epics/${epic.id}`, {
            method: 'PUT', token,
            body: { name: form.name.value.trim(), color: form.color.value, orderIndex: Number(form.orderIndex.value || 0) },
          })
          : await api('/api/v1/epics', {
            method: 'POST', token,
            body: {
              key: form.key.value.trim(), name: form.name.value.trim(),
              color: form.color.value, orderIndex: Number(form.orderIndex.value || 0),
            },
          });
        await ensureOk(res, editing ? 'Не удалось обновить эпик' : 'Не удалось создать эпик');
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
            <div class="field"><label>Ключ</label><input name="key" required maxlength="32" placeholder="T-10" /></div>`}
          <div class="field"><label>Название</label><input name="title" required value="${escapeHtml(task?.title || '')}" /></div>
          <div class="field">
            <label>Эпик</label>
            <select name="epicId">
              <option value="">Без эпика</option>
              ${cache.epics.map((epic) => `
                <option value="${epic.id}" ${task?.epicId === epic.id ? 'selected' : ''}>${escapeHtml(epic.key)} · ${escapeHtml(epic.name)}</option>`).join('')}
            </select>
          </div>
          <div class="field"><label>Приоритет</label><input name="priority" type="number" value="${task?.priority ?? 0}" /></div>
          ${editing ? `
            <div class="field">
              <label>Статус</label>
              <select name="status">
                ${['TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED'].map((status) =>
                  `<option value="${status}" ${task.status === status ? 'selected' : ''}>${status}</option>`).join('')}
              </select>
            </div>` : ''}
          <div class="field field-wide">
            <label>Описание</label>
            <textarea name="description" rows="3">${escapeHtml(task?.description || '')}</textarea>
          </div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </form>`,
    });
    modal.body.querySelector('#task-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      try {
        if (editing) {
          const updateRes = await api(`/api/v1/tasks/${task.id}`, {
            method: 'PUT', token,
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
              method: 'PUT', token, body: { status: form.status.value },
            });
            await ensureOk(statusRes, 'Не удалось сменить статус');
          }
        }
        else {
          const res = await api('/api/v1/tasks', {
            method: 'POST', token,
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

  function openEstimatesModal(task) {
    if (!cache.disciplines.length) {
      showError?.('Сначала заведите роли на вкладке «Команда»');
      return;
    }
    const current = task.estimates || {};
    const ordered = [...cache.disciplines].sort((a, b) => {
      const order = ['BE', 'FE', 'QA', 'SA'];
      const ai = order.indexOf(a.code);
      const bi = order.indexOf(b.code);
      return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
    });
    const modal = openModal({
      title: `Оценки · ${task.key}`,
      bodyHtml: `
        <form id="estimates-form" class="modal-form">
          <p class="hint">Отметьте роли, участвующие в задаче, и укажите SP. Пустое SP = роль участвует без оценки.</p>
          <div class="estimate-head">
            <span>Роль</span>
            <span>В задаче</span>
            <span>SP</span>
          </div>
          ${ordered.map((d) => {
            const involved = Object.prototype.hasOwnProperty.call(current, d.id);
            const value = involved && current[d.id] != null ? current[d.id] : '';
            return `
              <div class="estimate-row">
                <div class="estimate-role">
                  <span class="role-code">${escapeHtml(d.code)}</span>
                  <span class="role-name">${escapeHtml(d.name)}</span>
                </div>
                <label class="check">
                  <input type="checkbox" name="role-${d.id}" ${involved ? 'checked' : ''} />
                  <span>да</span>
                </label>
                <input type="number" min="0" step="0.5" name="sp-${d.id}" placeholder="SP"
                  aria-label="Оценка ${escapeHtml(d.code)}" value="${escapeHtml(String(value))}" />
              </div>`;
          }).join('')}
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">Сохранить оценки</button>
          </div>
        </form>`,
    });
    modal.body.querySelector('#estimates-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      const estimates = {};
      for (const d of ordered) {
        if (!form[`role-${d.id}`].checked) {
          continue;
        }
        const raw = form[`sp-${d.id}`].value.trim();
        estimates[d.id] = raw === '' ? null : Number(raw);
      }
      try {
        const res = await api(`/api/v1/tasks/${task.id}/estimates`, {
          method: 'PUT', token, body: { estimates },
        });
        await ensureOk(res, 'Не удалось сохранить оценки');
        modal.close();
        await reload();
        onChanged?.();
      }
      catch (err) {
        modal.setError(err.message);
      }
    });
  }

  function openAllocateModal(task) {
    const roleIds = Object.keys(task.estimates || {});
    if (!roleIds.length) {
      showError?.('Сначала задайте роли и оценки задачи');
      return;
    }
    if (!cache.sprints.length) {
      showError?.('Сначала создайте спринт');
      return;
    }
    const existing = cache.allocations.filter((item) => item.taskId === task.id);
    const modal = openModal({
      title: `В спринт · ${task.key}`,
      bodyHtml: `
        <form id="allocate-form" class="modal-form">
          <p class="hint">Аллокации по ролям задачи. Пустой список снимает задачу с плана.</p>
          <div class="field">
            <label>Спринт</label>
            <select name="sprintId" required>
              ${cache.sprints.map((sprint) =>
                `<option value="${sprint.id}">${escapeHtml(sprint.name)} (#${sprint.number})</option>`).join('')}
            </select>
          </div>
          ${roleIds.map((id) => {
            const d = cache.disciplines.find((item) => item.id === id);
            const estimate = task.estimates[id];
            const current = existing.find((item) => item.disciplineId === id);
            const def = current?.plannedSp ?? estimate ?? '';
            const roleLabel = d ? `${d.code} · ${d.name}` : id;
            return `
              <div class="field">
                <label>Роль ${escapeHtml(roleLabel)} — planned SP${estimate != null ? ` (оценка ${escapeHtml(String(estimate))})` : ''}</label>
                <input name="sp-${id}" type="number" min="0" step="0.5" required value="${escapeHtml(String(def))}" />
              </div>`;
          }).join('')}
          <div class="actions">
            <button type="button" class="btn-ghost" id="btn-clear-alloc">Снять с плана</button>
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">Сохранить</button>
          </div>
        </form>`,
    });

    async function save(allocations) {
      modal.setError('');
      try {
        const res = await api(`/api/v1/plan/tasks/${task.id}/allocations`, {
          method: 'PUT', token, body: { allocations },
        });
        await ensureOk(res, 'Не удалось сохранить аллокации');
        modal.close();
        await reload();
        onChanged?.();
      }
      catch (err) {
        modal.setError(err.message);
      }
    }

    modal.body.querySelector('#btn-clear-alloc').onclick = () => save([]);
    modal.body.querySelector('#allocate-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      const sprintId = form.sprintId.value;
      const allocations = roleIds.map((id) => ({
        sprintId,
        disciplineId: id,
        plannedSp: Number(form[`sp-${id}`].value),
      }));
      await save(allocations);
    });
  }

  reload();
  return { reload };
}
