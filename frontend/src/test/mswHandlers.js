/**
 * @fileoverview MSW (Mock Service Worker) handlers for API mocking in tests.
 */
import {http, HttpResponse} from './mswServer';

/**
 * Generates default runtime handlers for MSW.
 *
 * @param {Object} [overrides={}] - Mock data overrides.
 * @param {Object} [overrides.models] - Mock model list response.
 * @param {Object} [overrides.status] - Mock provider status response.
 * @param {Array} [overrides.sessions] - Mock sessions list response.
 * @param {Object} [overrides.ragStatus] - Mock RAG status response.
 * @returns {Array} Array of MSW http handlers.
 */
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
            retrievalMode: 'lexical',
            retrievalStore: 'in-memory',
            embeddingProvider: 'ollama',
            embeddingModel: 'nomic-embed-text'
        })),
        http.get('/api/sessions', () => HttpResponse.json(sessions || []))
    ];
}
