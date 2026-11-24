// Базова адреса API береться з window.APP_CONFIG (config.js).
// Порожній рядок означає той самий хост, з якого відкрито сторінку: у Kubernetes
// Ingress спрямовує /api на бекенд, а / на фронтенд.
const baseUrl = (window.APP_CONFIG?.apiBaseUrl ?? '').replace(/\/$/, '');
const itemsUrl = `${baseUrl}/api/items`;

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!response.ok) {
    throw new Error(`${options.method ?? 'GET'} ${url}: HTTP ${response.status}`);
  }
  return response.status === 204 ? null : response.json();
}

export const getItems = () => request(itemsUrl);

export const createItem = (item) =>
  request(itemsUrl, { method: 'POST', body: JSON.stringify(item) });

export const updateItem = (id, item) =>
  request(`${itemsUrl}/${id}`, { method: 'PUT', body: JSON.stringify(item) });

export const deleteItem = (id) => request(`${itemsUrl}/${id}`, { method: 'DELETE' });
