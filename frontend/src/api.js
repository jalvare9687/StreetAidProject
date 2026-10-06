const configuredBase = import.meta.env.VITE_API_BASE_URL?.trim();
const apiBase = (configuredBase || '/api').replace(/\/$/, '');

async function request(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed (${response.status})`);
  }

  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : response.text();
}

export const lookup = (zip) => request(`/lookup?zip=${encodeURIComponent(zip)}`);
export const getLivePosts = (zip) => request(`/live?zip=${encodeURIComponent(zip)}`);
export const createLivePost = (post) => request('/live', {
  method: 'POST',
  body: JSON.stringify(post),
});
export const closeLivePost = (id) => request(`/live/${encodeURIComponent(id)}`, {
  method: 'DELETE',
});
