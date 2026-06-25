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
 * Builds an actionable API error from a backend or proxy response.
 *
 * @param {Response} response - The failed fetch response.
 * @param {Object} payload - Parsed JSON payload, if any.
 * @param {string} fallbackMessage - Message to use when the backend did not return JSON.
 * @returns {Error} The error to throw to callers.
 */
function responseError(response, payload, fallbackMessage) {
    return new Error(payload.error || `${fallbackMessage} HTTP ${response.status}.`);
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
        const error = responseError(response, payload, 'Failed to load RAG status.');
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
        throw responseError(response, payload, 'Failed to rebuild the RAG index.');
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
        throw responseError(response, payload, 'Failed to query the RAG workspace.');
    }
    return response.json();
}

/**
 * Compares one RAG question across multiple retrieval targets without saving a RAG session turn.
 *
 * @param {Object} params - The comparison parameters.
 * @param {string} params.question - The user's question.
 * @param {string} params.provider - The LLM provider.
 * @param {string} params.model - The model ID.
 * @param {string[]} [params.retrievalTargets] - Optional target list. Defaults to all backend-supported targets.
 * @returns {Promise<Object>} A promise that resolves to the RAG comparison response.
 * @throws {Error} If the request fails.
 */
export async function compareRagRetrievalTargets({question, provider, model, retrievalTargets}) {
    const response = await fetch('/api/rag/compare', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({question, provider, model, retrievalTargets})
    });
    if (!response.ok) {
        const payload = await parseJson(response);
        throw responseError(response, payload, 'Failed to compare RAG retrieval targets.');
    }
    return response.json();
}
