import { api } from '../api.js';
import { escapeHtml, ensureOk } from '../dom.js';
import { openModal } from '../modal.js';

const VACATION_TYPES = ['VACATION', 'SICK', 'DAYOFF'];

export function renderTeamTab(root, ctx) {
  const { token, isAdmin, onChanged, showError } = ctx;
  let disciplines = [];
  let members = [];
  let disciplineFilter = 'ALL';

  async function reload() {
    root.innerHTML = '<p class="meta">Загрузка команды…</p>';
    try {
      const [dRes, mRes] = await Promise.all([
        api('/api/v1/disciplines', { token }),
        api('/api/v1/team/members', { token }),
      ]);
      await ensureOk(dRes, 'Не удалось загрузить роли');
      await ensureOk(mRes, 'Не удалось загрузить сотрудников');
      disciplines = await dRes.json();
      members = await mRes.json();
      paint();
    }
    catch (err) {
      root.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
      showError?.(err);
    }
  }

  function paint() {
    const filtered = disciplineFilter === 'ALL'
      ? members
      : members.filter((member) => member.disciplineId === disciplineFilter);

    root.innerHTML = `
      <div class="admin-toolbar">
        <div>
          <h2 class="section-title">Команда и отпуска</h2>
          <p class="hint">Ёмкость спринта считается из velocity роли и доступности с учётом отпусков.</p>
        </div>
        <div class="actions">
          ${isAdmin ? `
            <button type="button" class="btn-ghost" id="btn-discipline">Новая роль</button>
            <button type="button" class="btn-primary" id="btn-member">Сотрудник</button>
          ` : ''}
          <button type="button" class="btn-ghost" id="btn-reload-team">Обновить</button>
        </div>
      </div>

      <h3 class="subsection">Роли (дисциплины) и velocity</h3>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>Код</th><th>Название</th><th>Velocity, SP/спринт</th><th></th></tr>
          </thead>
          <tbody>
            ${disciplines.length ? disciplines.map((d) => `
              <tr>
                <td class="mono">${escapeHtml(d.code)}</td>
                <td>${escapeHtml(d.name)}</td>
                <td>${escapeHtml(String(d.velocitySpPerSprint))}</td>
                <td class="row-actions">
                  ${isAdmin ? `<button type="button" class="linkish" data-edit-velocity="${d.id}">Velocity…</button>` : '—'}
                </td>
              </tr>`).join('') : '<tr><td colspan="4" class="cell-empty">Ролей пока нет</td></tr>'}
          </tbody>
        </table>
      </div>

      <div class="admin-toolbar" style="margin-top:1.5rem">
        <h3 class="subsection" style="margin:0">Сотрудники и отпуска</h3>
        <div class="toolbar" style="margin:0">
          <label for="discipline-filter">Роль</label>
          <select id="discipline-filter">
            <option value="ALL">Все</option>
            ${disciplines.map((d) =>
              `<option value="${d.id}" ${disciplineFilter === d.id ? 'selected' : ''}>${escapeHtml(d.code)}</option>`).join('')}
          </select>
        </div>
      </div>

      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>Имя</th><th>Роль</th><th>Отпуска</th><th></th></tr>
          </thead>
          <tbody>
            ${filtered.length ? filtered.map((member) => `
              <tr>
                <td>${escapeHtml(member.fullName)}${member.lead ? ' · lead' : ''}</td>
                <td class="mono">${escapeHtml(member.disciplineCode || '—')}</td>
                <td>${vacationSummary(member.vacations || [])}</td>
                <td class="row-actions">
                  ${isAdmin ? `
                    <button type="button" class="linkish" data-vacations="${member.id}">Отпуска…</button>
                  ` : '—'}
                </td>
              </tr>`).join('') : '<tr><td colspan="4" class="cell-empty">Нет сотрудников</td></tr>'}
          </tbody>
        </table>
      </div>

      ${isAdmin ? '' : `
        <h3 class="subsection">Мой отпуск</h3>
        <div class="actions">
          <button type="button" class="btn-primary" id="btn-my-vacation">Управлять своим отпуском</button>
        </div>`}
    `;

    root.querySelector('#btn-reload-team')?.addEventListener('click', () => reload());
    root.querySelector('#btn-discipline')?.addEventListener('click', () => openDisciplineModal());
    root.querySelector('#btn-member')?.addEventListener('click', () => openMemberModal());
    root.querySelector('#discipline-filter')?.addEventListener('change', (event) => {
      disciplineFilter = event.target.value;
      paint();
    });
    root.querySelectorAll('[data-edit-velocity]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const d = disciplines.find((item) => item.id === btn.dataset.editVelocity);
        openVelocityModal(d);
      });
    });
    root.querySelectorAll('[data-vacations]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const member = members.find((item) => item.id === btn.dataset.vacations);
        openVacationsModal(member);
      });
    });
    root.querySelector('#btn-my-vacation')?.addEventListener('click', () => openMyVacationModal());
  }

  function vacationSummary(vacations) {
    if (!vacations.length) {
      return '<span class="cell-empty">нет</span>';
    }
    return vacations.map((v) =>
      `<div class="mono">${escapeHtml(v.startDate)} — ${escapeHtml(v.endDate)} · ${escapeHtml(v.type)}</div>`).join('');
  }

  function openDisciplineModal() {
    const modal = openModal({
      title: 'Новая роль',
      bodyHtml: `
        <form id="discipline-form" class="modal-form">
          <div class="field"><label>Код</label><input name="code" required placeholder="BACKEND" /></div>
          <div class="field"><label>Название</label><input name="name" required placeholder="Разработка" /></div>
          <div class="field"><label>Velocity, SP / спринт</label><input name="velocity" type="number" min="0.1" step="0.1" required value="20" /></div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">Создать</button>
          </div>
        </form>`,
    });
    modal.body.querySelector('#discipline-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      try {
        const res = await api('/api/v1/disciplines', {
          method: 'POST',
          token,
          body: {
            code: form.code.value.trim(),
            name: form.name.value.trim(),
            velocitySpPerSprint: Number(form.velocity.value),
          },
        });
        await ensureOk(res, 'Не удалось создать роль');
        modal.close();
        await reload();
        onChanged?.();
      }
      catch (err) {
        modal.setError(err.message);
      }
    });
  }

  function openVelocityModal(discipline) {
    const modal = openModal({
      title: `Velocity · ${discipline.code}`,
      bodyHtml: `
        <form id="velocity-form" class="modal-form">
          <p class="hint">Меняет расчёт ёмкости спринтов для этой роли.</p>
          <div class="field">
            <label>SP за эталонный спринт</label>
            <input name="velocity" type="number" min="0.1" step="0.1" required value="${escapeHtml(String(discipline.velocitySpPerSprint))}" />
          </div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">Сохранить</button>
          </div>
        </form>`,
    });
    modal.body.querySelector('#velocity-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      modal.setError('');
      try {
        const res = await api(`/api/v1/disciplines/${discipline.id}/velocity`, {
          method: 'PUT',
          token,
          body: { velocitySpPerSprint: Number(event.target.velocity.value) },
        });
        await ensureOk(res, 'Не удалось обновить velocity');
        await api('/api/v1/plan/recalculate', { method: 'POST', token }).catch(() => {});
        modal.close();
        await reload();
        onChanged?.();
      }
      catch (err) {
        modal.setError(err.message);
      }
    });
  }

  function openMemberModal() {
    const modal = openModal({
      title: 'Новый сотрудник',
      bodyHtml: `
        <form id="member-form" class="modal-form">
          <div class="field"><label>User ID (Keycloak sub)</label><input name="userId" required placeholder="22222222-2222-..." /></div>
          <div class="field"><label>ФИО</label><input name="fullName" required /></div>
          <div class="field">
            <label>Роль</label>
            <select name="disciplineId" required>
              ${disciplines.map((d) => `<option value="${d.id}">${escapeHtml(d.code)} · ${escapeHtml(d.name)}</option>`).join('')}
            </select>
          </div>
          <div class="field"><label>Активен с</label><input name="activeFrom" type="date" required value="2026-01-01" /></div>
          <div class="field"><label><input type="checkbox" name="lead" /> Lead</label></div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">Создать</button>
          </div>
        </form>`,
    });
    modal.body.querySelector('#member-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      try {
        const res = await api('/api/v1/team/members', {
          method: 'POST',
          token,
          body: {
            userId: form.userId.value.trim(),
            fullName: form.fullName.value.trim(),
            disciplineId: form.disciplineId.value,
            lead: form.lead.checked,
            activeFrom: form.activeFrom.value,
            activeTo: null,
          },
        });
        await ensureOk(res, 'Не удалось добавить сотрудника');
        modal.close();
        await reload();
        onChanged?.();
      }
      catch (err) {
        modal.setError(err.message);
      }
    });
  }

  function openVacationsModal(member) {
    paintVacationsModal(member, `/api/v1/team/members/${member.id}/vacations`);
  }

  async function openMyVacationModal() {
    try {
      const res = await api('/api/v1/team/me', { token });
      await ensureOk(res, 'Профиль не найден');
      const me = await res.json();
      paintVacationsModal(me, '/api/v1/team/me/vacations');
    }
    catch (err) {
      showError?.(err);
    }
  }

  function paintVacationsModal(member, basePath) {
    const vacations = member.vacations || [];
    const modal = openModal({
      title: `Отпуска · ${member.fullName}`,
      bodyHtml: `
        <div class="modal-form">
          <div class="table-wrap">
            <table class="data-table">
              <thead><tr><th>Период</th><th>Тип</th><th></th></tr></thead>
              <tbody>
                ${vacations.length ? vacations.map((v) => `
                  <tr>
                    <td class="mono">${escapeHtml(v.startDate)} — ${escapeHtml(v.endDate)}</td>
                    <td>${escapeHtml(v.type)}</td>
                    <td class="row-actions">
                      <button type="button" class="linkish" data-edit-vac="${v.id}">Изменить</button>
                      <button type="button" class="linkish danger" data-del-vac="${v.id}">Удалить</button>
                    </td>
                  </tr>`).join('') : '<tr><td colspan="3" class="cell-empty">Отпусков нет</td></tr>'}
              </tbody>
            </table>
          </div>
          <div class="actions">
            <button type="button" class="btn-primary" id="btn-add-vac">Добавить отпуск</button>
            <button type="button" class="btn-ghost" data-close="1">Закрыть</button>
          </div>
        </div>`,
    });

    modal.body.querySelector('#btn-add-vac').onclick = () => openVacationForm(modal, member, basePath, null);
    modal.body.querySelectorAll('[data-edit-vac]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const vacation = vacations.find((item) => item.id === btn.dataset.editVac);
        openVacationForm(modal, member, basePath, vacation);
      });
    });
    modal.body.querySelectorAll('[data-del-vac]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (!confirm('Удалить отпуск?')) {
          return;
        }
        try {
          const res = await api(`${basePath}/${btn.dataset.delVac}`, { method: 'DELETE', token });
          await ensureOk(res, 'Не удалось удалить отпуск');
          modal.close();
          await reload();
          onChanged?.();
        }
        catch (err) {
          modal.setError(err.message);
        }
      });
    });
  }

  function openVacationForm(parentModal, member, basePath, vacation) {
    parentModal.close();
    const editing = Boolean(vacation);
    const modal = openModal({
      title: editing ? 'Изменить отпуск' : `Отпуск · ${member.fullName}`,
      bodyHtml: `
        <form id="vacation-form" class="modal-form">
          <div class="field"><label>Начало</label><input name="startDate" type="date" required value="${escapeHtml(vacation?.startDate || '')}" /></div>
          <div class="field"><label>Конец</label><input name="endDate" type="date" required value="${escapeHtml(vacation?.endDate || '')}" /></div>
          <div class="field">
            <label>Тип</label>
            <select name="type">
              ${VACATION_TYPES.map((type) =>
                `<option value="${type}" ${vacation?.type === type ? 'selected' : ''}>${type}</option>`).join('')}
            </select>
          </div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </form>`,
    });
    modal.body.querySelector('#vacation-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      const body = {
        startDate: form.startDate.value,
        endDate: form.endDate.value,
        type: form.type.value,
      };
      try {
        const res = editing
          ? await api(`${basePath}/${vacation.id}`, { method: 'PUT', token, body })
          : await api(basePath, { method: 'POST', token, body });
        await ensureOk(res, 'Не удалось сохранить отпуск');
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
