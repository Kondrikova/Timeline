/**
 * Единый контрол периода: start — end в одном блоке.
 * Возвращает HTML; имена полей по умолчанию startDate / endDate.
 */
export function dateRangeFieldHtml({
  label = 'Период',
  startName = 'startDate',
  endName = 'endDate',
  startValue = '',
  endValue = '',
  required = true,
  idPrefix = 'range',
} = {}) {
  const req = required ? 'required' : '';
  return `
    <div class="field field-wide date-range-field">
      <label for="${idPrefix}-start">${label}</label>
      <div class="date-range">
        <input id="${idPrefix}-start" name="${startName}" type="date" ${req}
          value="${escapeAttr(startValue)}" aria-label="Начало периода" />
        <span class="date-range-sep" aria-hidden="true">—</span>
        <input id="${idPrefix}-end" name="${endName}" type="date" ${req}
          value="${escapeAttr(endValue)}" aria-label="Конец периода" />
      </div>
    </div>`;
}

/** Синхронизация: конец не раньше начала. */
export function bindDateRange(form, { startName = 'startDate', endName = 'endDate' } = {}) {
  const start = form.elements.namedItem(startName);
  const end = form.elements.namedItem(endName);
  if (!start || !end) {
    return;
  }
  const sync = () => {
    if (start.value) {
      end.min = start.value;
    }
    else {
      end.removeAttribute('min');
    }
    if (end.value) {
      start.max = end.value;
    }
    else {
      start.removeAttribute('max');
    }
    if (start.value && end.value && end.value < start.value) {
      end.value = start.value;
    }
  };
  start.addEventListener('change', sync);
  end.addEventListener('change', sync);
  sync();
}

function escapeAttr(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll('<', '&lt;');
}
