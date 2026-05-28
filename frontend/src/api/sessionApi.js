/**
 * @fileoverview API client for session-related operations, including listing, getting, deleting, exporting, and importing sessions.
 */

/**
 * Safely parses a JSON response, returning an empty object on failure.
 *
 * @param {Response} response - The fetch response object.
 * @returns {Promise<Object>} The parsed JSON payload or an empty object.
 */
async function parseJson(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

/**
 * Fetches a list of chat sessions, with optional filtering.
 *
 * @param {Object} [filters] - Filter parameters.
 * @param {string} [filters.query=''] - Search query for session titles or messages.
 * @param {string} [filters.provider=''] - Filter by LLM provider.
 * @param {string} [filters.toolUsage=''] - Filter by tool usage.
 * @param {boolean} [filters.pending=false] - If true, only show pending sessions.
 * @param {string} [filters.mode=''] - Filter by session mode (e.g., 'chat', 'rag').
 * @returns {Promise<Object[]>} A promise that resolves to an array of session objects.
 * @throws {Error} If the request fails.
 */
export async function listSessions({query = '', provider = '', toolUsage = '', pending = false, mode = ''} = {}) {
    const params = new URLSearchParams();
    if (query) {
        params.set('query', query);
    }
    if (provider) {
        params.set('provider', provider);
    }
    if (toolUsage) {
        params.set('toolUsage', toolUsage);
    }
    if (pending) {
        params.set('pending', 'true');
    }
    if (mode) {
        params.set('mode', mode);
    }

    const search = params.toString() ? `?${params.toString()}` : '';
    const response = await fetch(`/api/sessions${search}`);
    if (!response.ok) {
        const payload = await parseJson(response);
        throw new Error(payload.error || 'Failed to load sessions.');
    }
    return response.json();
}

/**
 * Fetches a specific session by ID.
 *
 * @param {string} sessionId - The ID of the session to retrieve.
 * @returns {Promise<Object>} A promise that resolves to the session object.
 * @throws {Error} If the request fails.
 */
export async function getSession(sessionId) {
    const response = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}`);
    if (!response.ok) {
        const payload = await parseJson(response);
        throw new Error(payload.error || 'Failed to load session.');
    }
    return response.json();
}

/**
 * Deletes a session by ID.
 *
 * @param {string} sessionId - The ID of the session to delete.
 * @returns {Promise<void>}
 * @throws {Error} If the request fails.
 */
export async function deleteSession(sessionId) {
    const response = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}`, {
        method: 'DELETE'
    });
    if (!response.ok) {
        const payload = await parseJson(response);
        throw new Error(payload.error || 'Failed to delete session.');
    }
}

/**
 * Extracts the filename from the Content-Disposition header.
 *
 * @param {string} contentDisposition - The header value.
 * @param {string} sessionId - Fallback session ID for the filename.
 * @param {string} format - The export format.
 * @returns {string} The extracted or generated filename.
 */
function filenameFromDisposition(contentDisposition, sessionId, format) {
    const match = contentDisposition?.match(/filename="([^"]+)"/i);
    const extension = format === 'markdown' || format === 'md' ? 'md' : 'json';
    return match?.[1] || `${sessionId}.${extension}`;
}

/**
 * Exports a session in the specified format.
 *
 * @param {string} sessionId - The ID of the session to export.
 * @param {string} [format='json'] - The export format ('json' or 'markdown').
 * @returns {Promise<{blob: Blob, filename: string}>} A promise that resolves to the exported data.
 * @throws {Error} If the request fails.
 */
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

/**
 * Imports a session from a file.
 *
 * @param {File} file - The session file to import.
 * @returns {Promise<Object>} A promise that resolves to the imported session object.
 * @throws {Error} If the request fails.
 */
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
