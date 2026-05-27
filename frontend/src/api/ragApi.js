async function parseJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

export async function getRagStatus() {
  const response = await fetch('/api/rag/status');
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to load RAG status.');
  }
  return response.json();
}

export async function rebuildRagIndex() {
  const response = await fetch('/api/rag/index', { method: 'POST' });
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to rebuild the RAG index.');
  }
  return response.json();
}

export async function queryRag({ question, provider, model }) {
  const response = await fetch('/api/rag/query', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ question, provider, model })
  });
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to query the RAG workspace.');
  }
  return response.json();
}
