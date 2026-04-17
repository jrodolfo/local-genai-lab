async function parseJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

export async function listAvailableModels() {
  // The selector is provider-aware: Ollama returns installed local models and Bedrock returns
  // discovered inference profiles or the configured fallback model id.
  const response = await fetch('/api/models');
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to load available models.');
  }
  return response.json();
}
