/**
 * @fileoverview API client for RAG (Retrieval-Augmented Generation) operations.
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
 * Fetches the current RAG status from the backend.
 *
 * @returns {Promise<Object>} A promise that resolves to the RAG status object.
 * @throws {Error} If the request fails.
 */
export async function getRagStatus() {
    const response = await fetch('/api/rag/status');
    if (!response.ok) {
        const payload = await parseJson(response);
        const error = new Error(payload.error || `Failed to load RAG status. HTTP ${response.status}.`);
        error.status = response.status;
        throw error;
    }
    return response.json();
}

/**
 * Triggers a rebuild of the RAG index in the backend.
 *
 * @returns {Promise<Object>} A promise that resolves to the response payload.
 * @throws {Error} If the request fails.
 */
export async function rebuildRagIndex() {
    const response = await fetch('/api/rag/index', {method: 'POST'});
    if (!response.ok) {
        const payload = await parseJson(response);
        throw new Error(payload.error || 'Failed to rebuild the RAG index.');
    }
    return response.json();
}

/**
 * Sends a question to the RAG workspace and retrieves an answer with sources.
 *
 * @param {Object} params - The query parameters.
 * @param {string} params.question - The user's question.
 * @param {string} params.provider - The LLM provider.
 * @param {string} params.model - The model ID.
 * @param {string} params.sessionId - The active session ID.
 * @param {string} [params.retrievalTarget] - Optional per-question retrieval target.
 * Supported values are `lexical`, `vector:in-memory`, and `vector:qdrant`.
 * @returns {Promise<Object>} A promise that resolves to the RAG query response.
 * @throws {Error} If the request fails.
 */
export async function queryRag({question, provider, model, sessionId, retrievalTarget}) {
    const response = await fetch('/api/rag/query', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({question, provider, model, sessionId, retrievalTarget})
    });
    if (!response.ok) {
        const payload = await parseJson(response);
        throw new Error(payload.error || 'Failed to query the RAG workspace.');
    }
    return response.json();
}
