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
 * Fetches backend-approved provider and model selector options.
 *
 * The backend filters providers to those configured in the current process and
 * returns provider-specific models for the requested provider.
 *
 * @param {string} [provider] - Optional provider ID to filter models.
 * @returns {Promise<Object>} Provider/model selector payload.
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
 * Fetches a provider health summary for the Agent page status banner.
 *
 * @param {string} [provider] - Optional provider ID to check status for.
 * @returns {Promise<Object>} Provider status payload with status, message, and optional model diagnostics.
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
