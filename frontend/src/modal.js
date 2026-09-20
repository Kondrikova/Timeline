import { escapeHtml } from './dom.js';

/**
 * Модальное окно: заголовок + HTML тела + опциональный footer.
 * Возвращает { root, body, close, setError }.
 */
export function openModal({ title, bodyHtml, onClose }) {
  closeModal();

  const root = document.createElement('div');
  root.className = 'modal-root';
  root.innerHTML = `
    <div class="modal-backdrop" data-close="1"></div>
    <div class="modal" role="dialog" aria-modal="true" aria-label="${escapeHtml(title)}">
      <header class="modal-header">
        <h2>${escapeHtml(title)}</h2>
        <button type="button" class="modal-close" data-close="1" aria-label="Закрыть">×</button>
      </header>
      <div class="modal-body"></div>
      <p class="error modal-error" hidden></p>
    </div>
  `;
  root.querySelector('.modal-body').innerHTML = bodyHtml;
  document.body.appendChild(root);
  document.body.classList.add('modal-open');

  const close = () => {
    root.remove();
    document.body.classList.remove('modal-open');
    onClose?.();
  };

  root.addEventListener('click', (event) => {
    if (event.target.closest('[data-close]')) {
      close();
    }
  });

  const onKey = (event) => {
    if (event.key === 'Escape') {
      close();
      document.removeEventListener('keydown', onKey);
    }
  };
  document.addEventListener('keydown', onKey);

  const firstInput = root.querySelector('input, select, textarea, button:not(.modal-close)');
  firstInput?.focus();

  return {
    root,
    body: root.querySelector('.modal-body'),
    close,
    setError(message) {
      const el = root.querySelector('.modal-error');
      if (!message) {
        el.hidden = true;
        el.textContent = '';
        return;
      }
      el.hidden = false;
      el.textContent = message;
    },
  };
}

export function closeModal() {
  document.querySelector('.modal-root')?.remove();
  document.body.classList.remove('modal-open');
}
