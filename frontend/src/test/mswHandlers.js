import { HttpResponse, http } from './mswServer';

export function defaultRuntimeHandlers(overrides = {}) {
  const {
    models,
    status,
    sessions,
    ragStatus
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
    http.get('/api/rag/status', () => HttpResponse.json(ragStatus || {
      enabled: false,
      indexed: false,
      corpusRoot: '/repo/docs',
      documentCount: 0,
      chunkCount: 0,
      retrievalMode: 'lexical'
    })),
    http.get('/api/sessions', () => HttpResponse.json(sessions || []))
  ];
}
