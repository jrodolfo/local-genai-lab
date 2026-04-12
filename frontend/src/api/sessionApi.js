async function parseJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

export async function listSessions(query = '') {
  const search = query ? `?q=${encodeURIComponent(query)}` : '';
  const response = await fetch(`/api/sessions${search}`);
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

function filenameFromDisposition(contentDisposition, sessionId, format) {
  const match = contentDisposition?.match(/filename="([^"]+)"/i);
  const extension = format === 'markdown' || format === 'md' ? 'md' : 'json';
  return match?.[1] || `${sessionId}.${extension}`;
}

export async function exportSession(sessionId, format = 'json') {
  const response = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/export?format=${encodeURIComponent(format)}`);
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to export session.');
  }

  return {
    blob: await response.blob(),
    filename: filenameFromDisposition(response.headers.get('Content-Disposition'), sessionId, format)
  };
}

export async function importSession(file) {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch('/api/sessions/import', {
    method: 'POST',
    body: formData
  });
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to import session.');
  }
  return response.json();
}
