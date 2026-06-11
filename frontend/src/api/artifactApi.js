/**
 * @fileoverview API client for artifact-related operations.
 * Provides functions to list and preview artifacts generated during tool execution.
 */

/**
 * Parses the JSON response from a fetch request.
 * Returns an empty object if parsing fails.
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
 * Fetches a list of artifact files for a specific run directory.
 *
 * @param {string} runDir - The backend-visible directory path of the tool run.
 * @returns {Promise<Object[]>} Artifact file descriptors with absolute path, relative path, and previewability.
 * @throws {Error} If the request fails or the server returns an error.
 */
export async function listArtifacts(runDir) {
    const response = await fetch(`/api/artifacts/files?runDir=${encodeURIComponent(runDir)}`);
    if (!response.ok) {
        const payload = await parseJson(response);
        const error = new Error(payload.error || 'Failed to list artifact files.');
        error.status = response.status;
        throw error;
    }
    return response.json();
}

/**
 * Fetches a preview of a specific artifact file.
 *
 * @param {string} path - The backend-visible full path of the artifact file to preview.
 * @returns {Promise<Object>} Artifact preview payload including content, content type, size, and truncation state.
 * @throws {Error} If the request fails or the server returns an error.
 */
export async function previewArtifact(path) {
    const response = await fetch(`/api/artifacts/preview?path=${encodeURIComponent(path)}`);
    if (!response.ok) {
        const payload = await parseJson(response);
        const error = new Error(payload.error || 'Failed to preview artifact.');
        error.status = response.status;
        throw error;
    }
    return response.json();
}
