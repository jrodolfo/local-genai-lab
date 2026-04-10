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

export async function streamMessage({ message, model, onToken, onMetadata }) {
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
    const chunks = buffer.split('\n\n');
    buffer = chunks.pop() || '';

    for (const chunk of chunks) {
      const event = parseSseEvent(chunk);
      if (!event) {
        continue;
      }

      if (event.type === 'metadata') {
        if (onMetadata) {
          onMetadata(event.data);
        }
        continue;
      }

      if (event.data.trim() === '[DONE]') {
        return;
      }

      onToken(event.data);
    }
  }
}

function parseSseEvent(chunk) {
  const lines = chunk.split('\n');
  let eventName = 'message';
  const dataLines = [];

  for (const line of lines) {
    const normalized = line.endsWith('\r') ? line.slice(0, -1) : line;
    if (normalized.startsWith('event:')) {
      eventName = normalized.slice(6).trim();
    } else if (normalized.startsWith('data:')) {
      dataLines.push(normalized.slice(5));
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  const payload = dataLines.join('\n');
  if (eventName === 'metadata') {
    try {
      return { type: 'metadata', data: JSON.parse(payload) };
    } catch {
      return null;
    }
  }

  return { type: 'message', data: payload };
}

async function safeParseJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}
