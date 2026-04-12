async function parseJson(response) {
  try {
    return await response.json();
  } catch {
    return {};
  }
}

export async function listArtifacts(runDir) {
  const response = await fetch(`/api/artifacts/files?runDir=${encodeURIComponent(runDir)}`);
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to list artifact files.');
  }
  return response.json();
}

export async function previewArtifact(path) {
  const response = await fetch(`/api/artifacts/preview?path=${encodeURIComponent(path)}`);
  if (!response.ok) {
    const payload = await parseJson(response);
    throw new Error(payload.error || 'Failed to preview artifact.');
  }
  return response.json();
}
