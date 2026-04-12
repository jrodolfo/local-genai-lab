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

function filenameFromDisposition(contentDisposition, sessionId) {
  const match = contentDisposition?.match(/filename="([^"]+)"/i);
  return match?.[1] || `${sessionId}.json`;
}

export async function exportSession(sessionId) {
  const response = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/export`);
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to export session.');
  }

  return {
    blob: await response.blob(),
    filename: filenameFromDisposition(response.headers.get('Content-Disposition'), sessionId)
  };
}
