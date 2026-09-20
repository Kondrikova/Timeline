import { api } from '../api.js';
import { escapeHtml, ensureOk } from '../dom.js';
import { openModal } from '../modal.js';

const STATES = ['PLANNED', 'ACTIVE', 'CLOSED'];

export async function loadSprints(token) {
  const res = await api('/api/v1/sprints', { token });
  await ensureOk(res, 'Не удалось загрузить спринты');
  return res.json();
}

export function renderSprintsTab(root, ctx) {
  const { token, isAdmin, onChanged, showError } = ctx;
  let sprints = [];

  async function reload() {
    root.innerHTML = '<p class="meta">Загрузка спринтов…</p>';
    try {
      sprints = await loadSprints(token);
      paint();
    }
    catch (err) {
      root.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
      showError?.(err);
    }
  }

  function paint() {
    root.innerHTML = `
      <div class="admin-toolbar">
        <div>
          <h2 class="section-title">Спринты</h2>
          <p class="hint">Создание, перенос дат и смена состояния. Удаление запускает сагу.</p>
        </div>
        <div class="actions">
          ${isAdmin ? `<button type="button" class="btn-primary" id="btn-sprint">Новый спринт</button>` : ''}
          <button type="button" class="btn-ghost" id="btn-reload-sprints">Обновить</button>
        </div>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>#</th><th>Название</th><th>Период</th><th>Состояние</th><th>Раб. дни</th><th></th>
            </tr>
          </thead>
          <tbody>
            ${sprints.length ? sprints.map((sprint) => `
              <tr>
                <td class="mono">${sprint.number}</td>
                <td>${escapeHtml(sprint.name)}</td>
                <td>${escapeHtml(sprint.startDate)} — ${escapeHtml(sprint.endDate)}</td>
                <td><span class="status">${escapeHtml(sprint.state)}</span></td>
                <td>${sprint.workingDays}</td>
                <td class="row-actions">
                  ${isAdmin && sprint.state !== 'DELETING' ? `
                    <button type="button" class="linkish" data-edit-sprint="${sprint.id}">Изменить</button>
                    <button type="button" class="linkish danger" data-delete-sprint="${sprint.id}">Удалить</button>
                  ` : '—'}
                </td>
              </tr>`).join('') : '<tr><td colspan="6" class="cell-empty">Спринтов пока нет</td></tr>'}
          </tbody>
        </table>
      </div>
    `;

    root.querySelector('#btn-reload-sprints')?.addEventListener('click', () => reload());
    root.querySelector('#btn-sprint')?.addEventListener('click', () => openSprintModal());
    root.querySelectorAll('[data-edit-sprint]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const sprint = sprints.find((item) => item.id === btn.dataset.editSprint);
        openSprintModal(sprint);
      });
    });
    root.querySelectorAll('[data-delete-sprint]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const sprint = sprints.find((item) => item.id === btn.dataset.deleteSprint);
        if (!sprint || !confirm(`Удалить спринт «${sprint.name}»? Аллокации будут сняты.`)) {
          return;
        }
        try {
          const res = await api(`/api/v1/sprints/${sprint.id}`, { method: 'DELETE', token });
          await ensureOk(res, 'Не удалось удалить спринт');
          await reload();
          onChanged?.();
        }
        catch (err) {
          showError?.(err);
        }
      });
    });
  }

  function openSprintModal(sprint) {
    const editing = Boolean(sprint);
    const nextNumber = sprints.reduce((max, item) => Math.max(max, item.number), 0) + 1;
    const modal = openModal({
      title: editing ? `Спринт ${sprint.number}` : 'Новый спринт',
      bodyHtml: `
        <form id="sprint-form" class="modal-form">
          ${editing ? '' : `
            <div class="field">
              <label for="sprint-number">Номер</label>
              <input id="sprint-number" name="number" type="number" min="1" required value="${nextNumber}" />
            </div>`}
          <div class="field">
            <label for="sprint-name">Название</label>
            <input id="sprint-name" name="name" required value="${escapeHtml(sprint?.name || `Sprint ${nextNumber}`)}" />
          </div>
          <div class="field">
            <label for="sprint-start">Начало</label>
            <input id="sprint-start" name="startDate" type="date" required value="${escapeHtml(sprint?.startDate || '')}" />
          </div>
          <div class="field">
            <label for="sprint-end">Конец</label>
            <input id="sprint-end" name="endDate" type="date" required value="${escapeHtml(sprint?.endDate || '')}" />
          </div>
          ${editing ? `
            <div class="field">
              <label for="sprint-state">Состояние</label>
              <select id="sprint-state" name="state">
                ${STATES.map((state) =>
                  `<option value="${state}" ${sprint.state === state ? 'selected' : ''}>${state}</option>`).join('')}
              </select>
            </div>` : ''}
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </form>
      `,
    });

    modal.body.querySelector('#sprint-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      try {
        if (editing) {
          const patchRes = await api(`/api/v1/sprints/${sprint.id}`, {
            method: 'PATCH',
            token,
            body: {
              name: form.name.value.trim(),
              startDate: form.startDate.value,
              endDate: form.endDate.value,
            },
          });
          await ensureOk(patchRes, 'Не удалось изменить спринт');
          if (form.state && form.state.value !== sprint.state) {
            const stateRes = await api(`/api/v1/sprints/${sprint.id}/state`, {
              method: 'PUT',
              token,
              body: { state: form.state.value },
            });
            await ensureOk(stateRes, 'Не удалось сменить состояние');
          }
        }
        else {
          const res = await api('/api/v1/sprints', {
            method: 'POST',
            token,
            body: {
              number: Number(form.number.value),
              name: form.name.value.trim(),
              startDate: form.startDate.value,
              endDate: form.endDate.value,
            },
          });
          await ensureOk(res, 'Не удалось создать спринт');
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
