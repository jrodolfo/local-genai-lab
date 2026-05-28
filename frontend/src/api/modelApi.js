/**
 * @fileoverview API client for retrieving model and provider information.
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
 * Fetches the list of available models, optionally filtered by provider.
 *
 * @param {string} [provider] - Optional provider ID to filter models.
 * @returns {Promise<Object[]>} A promise that resolves to an array of model objects.
 * @throws {Error} If the request fails.
 */
export async function listAvailableModels(provider) {
    const params = new URLSearchParams();
    if (provider) {
        params.set('provider', provider);
    }
    const url = params.size > 0 ? `/api/models?${params.toString()}` : '/api/models';
    // The selector is provider-aware and backend-driven. The backend also filters the provider list
    // so the UI only offers providers configured in the current backend process.
    const response = await fetch(url);
    if (!response.ok) {
        const payload = await parseJson(response);
        throw new Error(payload.error || 'Failed to load available models.');
    }
    return response.json();
}

/**
 * Fetches the status of model providers.
 *
 * @param {string} [provider] - Optional provider ID to check status for.
 * @returns {Promise<Object>} A promise that resolves to the provider status object.
 * @throws {Error} If the request fails.
 */
export async function getProviderStatus(provider) {
    const params = new URLSearchParams();
    if (provider) {
        params.set('provider', provider);
    }
    const url = params.size > 0 ? `/api/models/status?${params.toString()}` : '/api/models/status';
    const response = await fetch(url);
    if (!response.ok) {
        const payload = await parseJson(response);
        throw new Error(payload.error || 'Failed to load provider status.');
    }
    return response.json();
}
