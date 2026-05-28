/**
 * @fileoverview API client for chat operations, including streaming and non-streaming message delivery.
 */

/**
 * Standard headers for JSON requests.
 * @type {Object}
 */
const JSON_HEADERS = {
    'Content-Type': 'application/json'
};

/**
 * Sends a chat message to the backend and waits for the full response.
 *
 * @param {Object} params - The message parameters.
 * @param {string} params.message - The message text.
 * @param {string} params.provider - The LLM provider (e.g., 'bedrock', 'openai').
 * @param {string} params.model - The model ID.
 * @param {string} params.sessionId - The active chat session ID.
 * @param {AbortSignal} [params.signal] - Optional signal to abort the request.
 * @returns {Promise<Object>} A promise that resolves to the chat response payload.
 * @throws {Error} If the request fails.
 */
export async function sendMessage({message, provider, model, sessionId, signal}) {
    const response = await fetch('/api/chat', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({message, provider, model, sessionId}),
        signal
    });

    if (!response.ok) {
        const payload = await safeParseJson(response);
        throw new Error(payload.error || 'Request failed.');
    }

    return response.json();
}

/**
 * Sends a chat message and streams the response via Server-Sent Events (SSE).
 *
 * @param {Object} params - The message parameters.
 * @param {string} params.message - The message text.
 * @param {string} params.provider - The LLM provider.
 * @param {string} params.model - The model ID.
 * @param {string} params.sessionId - The active chat session ID.
 * @param {Function} params.onEvent - Callback function invoked for each streamed event.
 * @param {AbortSignal} [params.signal] - Optional signal to abort the stream.
 * @returns {Promise<void>} A promise that resolves when the stream is complete.
 * @throws {Error} If the stream request fails or streaming is not supported.
 */
export async function streamMessage({message, provider, model, sessionId, onEvent, signal}) {
    const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: JSON_HEADERS,
        body: JSON.stringify({message, provider, model, sessionId}),
        signal
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
        const {done, value} = await reader.read();
        if (done) {
            break;
        }

        buffer += decoder.decode(value, {stream: true});
        const chunks = buffer.split('\n\n');
        buffer = chunks.pop() || '';

        for (const chunk of chunks) {
            // The backend emits a single named SSE event (`chat`) with typed JSON payloads. Ignore
            // malformed or unrelated chunks rather than failing the whole stream.
            const event = parseSseEvent(chunk);
            if (!event) {
                continue;
            }

            if (onEvent) {
                onEvent(event);
            }

            if (event.type === 'complete') {
                return;
            }
        }
    }
}

/**
 * Parses a raw SSE event chunk into a structured object.
 *
 * @param {string} chunk - The raw SSE chunk text.
 * @returns {Object|null} The parsed event payload or null if the chunk is invalid or not a 'chat' event.
 */
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
    if (eventName !== 'chat') {
        return null;
    }

    try {
        return JSON.parse(payload);
    } catch {
        return null;
    }
}

/**
 * Safely parses a JSON response, returning an empty object on failure.
 *
 * @param {Response} response - The fetch response object.
 * @returns {Promise<Object>} The parsed JSON payload or an empty object.
 */
async function safeParseJson(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}
