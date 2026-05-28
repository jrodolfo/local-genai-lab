async function parseJson(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

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
