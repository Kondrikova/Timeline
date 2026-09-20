import { api } from '../api.js';
import { escapeHtml, ensureOk } from '../dom.js';
import { openModal } from '../modal.js';
import { bindDateRange, dateRangeFieldHtml } from '../date-range.js';

const VACATION_TYPES = ['VACATION', 'SICK', 'DAYOFF'];
const ROLE_ORDER = ['BE', 'FE', 'QA', 'SA'];

export function renderTeamTab(root, ctx) {
  const { token, isAdmin, onChanged, showError } = ctx;
  let disciplines = [];
  let members = [];
  let directory = [];
  let me = null;
  let disciplineFilter = 'ALL';

  async function reload() {
    root.innerHTML = '<p class="meta">Загрузка команды…</p>';
    try {
      const requests = [
        api('/api/v1/disciplines', { token }),
        api('/api/v1/team/members', { token }),
        api('/api/v1/team/me', { token }),
      ];
      if (isAdmin) {
        requests.push(api('/api/v1/team/directory', { token }));
      }
      const responses = await Promise.all(requests);
      await ensureOk(responses[0], 'Не удалось загрузить роли');
      await ensureOk(responses[1], 'Не удалось загрузить сотрудников');
      disciplines = sortDisciplines(await responses[0].json());
      members = await responses[1].json();
      if (responses[2].ok) {
        me = await responses[2].json();
      }
      else if (responses[2].status === 404) {
        me = null;
      }
      else {
        await ensureOk(responses[2], 'Не удалось загрузить профиль');
      }
      if (isAdmin) {
        await ensureOk(responses[3], 'Не удалось загрузить каталог пользователей');
        directory = await responses[3].json();
      }
      paint();
    }
    catch (err) {
      root.innerHTML = `<p class="error">${escapeHtml(err.message)}</p>`;
      showError?.(err);
    }
  }

  function sortDisciplines(list) {
    return [...list].sort((a, b) => {
      const ai = ROLE_ORDER.indexOf(a.code);
      const bi = ROLE_ORDER.indexOf(b.code);
      const av = ai === -1 ? 99 : ai;
      const bv = bi === -1 ? 99 : bi;
      if (av !== bv) {
        return av - bv;
      }
      return String(a.code).localeCompare(String(b.code));
    });
  }

  function disciplineOptions(selectedId, { includeEmpty = false } = {}) {
    const empty = includeEmpty
      ? '<option value="">Не назначена</option>'
      : '';
    return empty + disciplines.map((d) =>
      `<option value="${d.id}" ${selectedId === d.id ? 'selected' : ''}>${escapeHtml(d.code)} · ${escapeHtml(d.name)}</option>`).join('');
  }

  function paint() {
    const filtered = disciplineFilter === 'ALL'
      ? members
      : members.filter((member) => member.disciplineId === disciplineFilter);

    root.innerHTML = `
      <div class="admin-toolbar">
        <div>
          <h2 class="section-title">Команда и отпуска</h2>
          <p class="hint">Роли фиксированы: FE, BE, QA, SA. Ёмкость считается из velocity роли и доступности с учётом отпусков.</p>
        </div>
        <div class="actions">
          <button type="button" class="btn-ghost" id="btn-reload-team">Обновить</button>
        </div>
      </div>

      <section class="panel-block">
        <h3 class="subsection">Моя роль</h3>
        <p class="hint">Укажите профессиональную роль — она нужна для расчёта ёмкости и оценок.</p>
        <form id="my-role-form" class="inline-form">
          <label for="my-discipline">Роль</label>
          <select id="my-discipline" name="disciplineId" required>
            ${disciplineOptions(me?.disciplineId, { includeEmpty: !me })}
          </select>
          <button type="submit" class="btn-primary">Сохранить</button>
          <span class="meta" id="my-role-status">${me ? escapeHtml(me.disciplineCode || '') : 'ещё не указана'}</span>
        </form>
      </section>

      <h3 class="subsection">Роли и velocity</h3>
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
              </tr>`).join('') : '<tr><td colspan="4" class="cell-empty">Роли не загружены (нужен seed FE/BE/QA/SA)</td></tr>'}
          </tbody>
        </table>
      </div>

      ${isAdmin ? renderDirectory() : ''}

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
                  ${isAdmin || (me && me.id === member.id) ? `
                    <button type="button" class="linkish" data-vacations="${member.id}">Отпуска…</button>
                  ` : '—'}
                </td>
              </tr>`).join('') : '<tr><td colspan="4" class="cell-empty">Нет сотрудников с назначенной ролью</td></tr>'}
          </tbody>
        </table>
      </div>
    `;

    root.querySelector('#btn-reload-team')?.addEventListener('click', () => reload());
    root.querySelector('#my-role-form')?.addEventListener('submit', onSaveMyRole);
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
        if (me && member && me.id === member.id && !isAdmin) {
          paintVacationsModal(member, '/api/v1/team/me/vacations');
        }
        else {
          openVacationsModal(member);
        }
      });
    });
    root.querySelectorAll('[data-assign-user]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const user = directory.find((item) => item.userId === btn.dataset.assignUser);
        openAssignModal(user);
      });
    });
  }

  function renderDirectory() {
    return `
      <h3 class="subsection">Пользователи Keycloak</h3>
      <p class="hint">Назначьте роль зарегистрированному пользователю — вводить Keycloak ID не нужно.</p>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>Имя</th><th>Логин</th><th>Роль</th><th></th></tr>
          </thead>
          <tbody>
            ${directory.length ? directory.map((user) => `
              <tr>
                <td>${escapeHtml(user.fullName || '—')}${user.lead ? ' · lead' : ''}</td>
                <td class="mono">${escapeHtml(user.username || '')}</td>
                <td class="mono">${user.inTeam ? escapeHtml(user.disciplineCode || '—') : '<span class="cell-empty">не в команде</span>'}</td>
                <td class="row-actions">
                  <button type="button" class="linkish" data-assign-user="${escapeHtml(user.userId)}">Роль…</button>
                </td>
              </tr>`).join('') : '<tr><td colspan="4" class="cell-empty">Каталог пуст</td></tr>'}
          </tbody>
        </table>
      </div>`;
  }

  async function onSaveMyRole(event) {
    event.preventDefault();
    const select = root.querySelector('#my-discipline');
    const status = root.querySelector('#my-role-status');
    if (!select?.value) {
      return;
    }
    try {
      const res = await api('/api/v1/team/me/discipline', {
        method: 'PUT',
        token,
        body: { disciplineId: select.value },
      });
      await ensureOk(res, 'Не удалось сохранить роль');
      me = await res.json();
      if (status) {
        status.textContent = me.disciplineCode || 'сохранено';
      }
      await reload();
      onChanged?.();
    }
    catch (err) {
      showError?.(err);
    }
  }

  function openAssignModal(user) {
    const modal = openModal({
      title: `Роль · ${user.fullName || user.username}`,
      bodyHtml: `
        <form id="assign-form" class="modal-form">
          <p class="hint">${escapeHtml(user.username || '')}${user.email ? ` · ${escapeHtml(user.email)}` : ''}</p>
          <div class="field">
            <label>Профессиональная роль</label>
            <select name="disciplineId" required>
              ${disciplineOptions(user.disciplineId)}
            </select>
          </div>
          <div class="field"><label><input type="checkbox" name="lead" ${user.lead ? 'checked' : ''} /> Lead</label></div>
          <div class="actions">
            <button type="button" class="btn-ghost" data-close="1">Отмена</button>
            <button type="submit" class="btn-primary">Назначить</button>
          </div>
        </form>`,
    });
    modal.body.querySelector('#assign-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = event.target;
      modal.setError('');
      try {
        const res = await api(`/api/v1/team/directory/${encodeURIComponent(user.userId)}/discipline`, {
          method: 'PUT',
          token,
          body: {
            disciplineId: form.disciplineId.value,
            lead: form.lead.checked,
          },
        });
        await ensureOk(res, 'Не удалось назначить роль');
        modal.close();
        await reload();
        onChanged?.();
      }
      catch (err) {
        modal.setError(err.message);
      }
    });
  }

  function vacationSummary(vacations) {
    if (!vacations.length) {
      return '<span class="cell-empty">нет</span>';
    }
    return vacations.map((v) =>
      `<div class="mono">${escapeHtml(v.startDate)} — ${escapeHtml(v.endDate)} · ${escapeHtml(v.type)}</div>`).join('');
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

  function openVacationsModal(member) {
    paintVacationsModal(member, `/api/v1/team/members/${member.id}/vacations`);
  }

  function paintVacationsModal(member, basePath) {
    let working = { ...(member || {}), vacations: [...(member.vacations || [])] };
    const modal = openModal({
      title: `Отпуска · ${member.fullName}`,
      wide: true,
      bodyHtml: `<div class="vacation-panel" id="vacation-panel"></div>`,
    });

    function renderList() {
      const vacations = working.vacations || [];
      modal.body.querySelector('#vacation-panel').innerHTML = `
        <div class="vacation-list">
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
        </div>`;

      modal.body.querySelector('#btn-add-vac').onclick = () => renderForm(null);
      modal.body.querySelectorAll('[data-edit-vac]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const vacation = vacations.find((item) => item.id === btn.dataset.editVac);
          renderForm(vacation);
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
            working.vacations = working.vacations.filter((item) => item.id !== btn.dataset.delVac);
            modal.setError('');
            renderList();
            await reload();
            onChanged?.();
          }
          catch (err) {
            modal.setError(err.message);
          }
        });
      });
    }

    function renderForm(vacation) {
      const editing = Boolean(vacation);
      modal.body.querySelector('#vacation-panel').innerHTML = `
        <form id="vacation-form" class="modal-form">
          <p class="hint">${editing ? 'Изменение отпуска' : 'Новый отпуск'} · ${escapeHtml(member.fullName)}</p>
          ${dateRangeFieldHtml({
            label: 'Период отпуска',
            startValue: vacation?.startDate || '',
            endValue: vacation?.endDate || '',
            idPrefix: 'vac',
          })}
          <div class="field">
            <label>Тип</label>
            <select name="type">
              ${VACATION_TYPES.map((type) =>
                `<option value="${type}" ${vacation?.type === type ? 'selected' : ''}>${type}</option>`).join('')}
            </select>
          </div>
          <div class="actions">
            <button type="button" class="btn-ghost" id="btn-vac-back">Назад к списку</button>
            <button type="submit" class="btn-primary">${editing ? 'Сохранить' : 'Создать'}</button>
          </div>
        </form>`;

      const form = modal.body.querySelector('#vacation-form');
      bindDateRange(form);
      modal.body.querySelector('#btn-vac-back').onclick = () => {
        modal.setError('');
        renderList();
      };
      form.addEventListener('submit', async (event) => {
        event.preventDefault();
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
          const saved = await res.json();
          if (editing) {
            working.vacations = working.vacations.map((item) =>
              (item.id === vacation.id ? saved : item));
          }
          else {
            working.vacations = [...working.vacations, saved];
          }
          modal.setError('');
          renderList();
          onChanged?.();
        }
        catch (err) {
          modal.setError(err.message);
        }
      });
    }

    renderList();
  }

  reload();
  return { reload };
}
