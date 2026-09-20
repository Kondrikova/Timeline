/** Общие DOM/HTML helpers. */

export function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

export async function readError(res) {
  try {
    const body = await res.json();
    return body.detail || body.title || body.code || JSON.stringify(body);
  }
  catch {
    return res.text();
  }
}

export function ensureOk(res, message) {
  if (res.ok) {
    return res;
  }
  return readError(res).then((detail) => {
    throw new Error(`${message} (${res.status}): ${detail}`);
  });
}
