async function parseJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

export async function listSessions() {
  const response = await fetch('/api/sessions');
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to load sessions.');
  }
  return response.json();
}

export async function getSession(sessionId) {
  const response = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}`);
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to load session.');
  }
  return response.json();
}

export async function deleteSession(sessionId) {
  const response = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE'
  });
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to delete session.');
  }
}
