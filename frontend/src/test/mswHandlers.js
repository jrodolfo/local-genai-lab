import { HttpResponse, http } from './mswServer';

export function defaultRuntimeHandlers(overrides = {}) {
  const {
    models,
    status,
    sessions
  } = overrides;

  return [
    http.get('/api/models', () => HttpResponse.json(models || {
      provider: 'ollama',
      defaultProvider: 'ollama',
      providers: ['ollama'],
      defaultModel: 'llama3:8b',
      models: ['llama3:8b']
    })),
    http.get('/api/models/status', () => HttpResponse.json(status || {
      provider: 'ollama',
      status: 'ready',
      message: 'Ollama is reachable and ready.'
    })),
    http.get('/api/sessions', () => HttpResponse.json(sessions || []))
  ];
}
