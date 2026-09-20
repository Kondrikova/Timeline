const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8090';
const REALM = 'timeline';
const CLIENT_ID = 'timeline-web';
const API_BASE = import.meta.env.VITE_API_BASE || '';

const storageKey = 'timeline.auth';

export function loadAuth() {
  try {
    return JSON.parse(localStorage.getItem(storageKey) || 'null');
  }
  catch {
    return null;
  }
}

export function saveAuth(auth) {
  localStorage.setItem(storageKey, JSON.stringify(auth));
}

export function clearAuth() {
  localStorage.removeItem(storageKey);
}

export async function login(username, password) {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: CLIENT_ID,
    username,
    password,
  });
  const res = await fetch(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    },
  );
  if (!res.ok) {
    throw new Error('Не удалось войти. Проверьте логин и пароль.');
  }
  const token = await res.json();
  const auth = {
    accessToken: token.access_token,
    refreshToken: token.refresh_token,
    expiresAt: Date.now() + token.expires_in * 1000,
    username,
  };
  saveAuth(auth);
  return auth;
}

export async function api(path, { method = 'GET', body, token, etag } = {}) {
  const headers = {
    Authorization: `Bearer ${token}`,
  };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (etag) {
    headers['If-None-Match'] = etag;
  }
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return res;
}
