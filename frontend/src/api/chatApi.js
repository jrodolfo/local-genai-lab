const JSON_HEADERS = {
  'Content-Type': 'application/json'
};

export async function sendMessage({ message, model }) {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ message, model })
  });

  if (!response.ok) {
    const payload = await safeParseJson(response);
    throw new Error(payload.error || 'Request failed.');
  }

  return response.json();
}

export async function streamMessage({ message, model, onToken }) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ message, model })
  });

  if (!response.ok) {
    const payload = await safeParseJson(response);
    throw new Error(payload.error || 'Stream request failed.');
  }

  if (!response.body) {
    throw new Error('Streaming is not supported by this browser.');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      const token = parseSseDataLine(line);
      if (token === null) {
        continue;
      }
      if (token.trim() === '[DONE]') {
        return;
      }
      onToken(token);
    }
  }
}

function parseSseDataLine(line) {
  const normalized = line.endsWith('\r') ? line.slice(0, -1) : line;
  if (!normalized.startsWith('data:')) {
    return null;
  }
  return normalized.slice(5);
}

async function safeParseJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}
