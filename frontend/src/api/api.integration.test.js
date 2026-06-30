// @vitest-environment node

import {listArtifacts, previewArtifact} from './artifactApi';
import {sendMessage, streamMessage} from './chatApi';
import {getProviderStatus, listAvailableModels} from './modelApi';
import {compareRagRetrievalTargets, queryRag} from './ragApi';
import {exportSession, importSession, listSessions} from './sessionApi';
import {http, HttpResponse, server, sseResponse} from '../test/mswServer';

const API_BASE_URL = 'http://localhost';

function apiPath(path) {
    return `${API_BASE_URL}${path}`;
}

let interceptedFetch;

beforeAll(() => {
    interceptedFetch = globalThis.fetch;
    globalThis.fetch = (input, init) => {
        if (typeof input === 'string' && input.startsWith('/')) {
            return interceptedFetch(new URL(input, API_BASE_URL).toString(), init);
        }
        return interceptedFetch(input, init);
    };
});

afterAll(() => {
    globalThis.fetch = interceptedFetch;
});

async function readBlobText(blob) {
    if (typeof blob?.text === 'function') {
        return blob.text();
    }
    if (typeof blob?.arrayBuffer === 'function') {
        return new TextDecoder().decode(await blob.arrayBuffer());
    }
    return new Response(blob).text();
}

describe('frontend api integration', () => {
    it('sends a chat request and returns the backend payload', async () => {
        server.use(
            http.post(apiPath('/api/chat'), async ({request}) => {
                const body = await request.json();
                expect(body).toMatchObject({
                    message: 'run aws audit',
                    provider: 'ollama',
                    model: 'llama3:8b'
                });
                return HttpResponse.json({
                    response: 'Audit complete.',
                    model: 'llama3:8b',
                    sessionId: 'session-123',
                    tool: {used: true, name: 'aws_region_audit', status: 'success', summary: 'AWS audit completed.'},
                    toolResult: {type: 'audit_summary', successCount: 10, failureCount: 0}
                });
            })
        );

        const result = await sendMessage({message: 'run aws audit', provider: 'ollama', model: 'llama3:8b'});

        expect(result.response).toBe('Audit complete.');
        expect(result.sessionId).toBe('session-123');
        expect(result.tool.name).toBe('aws_region_audit');
    });

    it('surfaces backend chat errors', async () => {
        server.use(
            http.post(apiPath('/api/chat'), () => HttpResponse.json({error: 'Backend unavailable.'}, {status: 503}))
        );

        await expect(sendMessage({message: 'hello', provider: 'ollama', model: 'llama3:8b'}))
            .rejects.toThrow('Backend unavailable.');
    });

    it('streams typed SSE chat events through the chat api', async () => {
        server.use(
            http.post(apiPath('/api/chat/stream'), () => sseResponse([
                {type: 'start', sessionId: 'session-123'},
                {type: 'tool-execution-started', toolName: 'aws_region_audit'},
                {type: 'delta', text: 'Hello'},
                {type: 'complete', sessionId: 'session-123', metadata: {provider: 'bedrock', totalTokens: 42}}
            ]))
        );

        const events = [];

        await streamMessage({
            message: 'run aws audit',
            provider: 'ollama',
            model: 'llama3:8b',
            onEvent: (event) => events.push(event)
        });

        expect(events).toEqual([
            {type: 'start', sessionId: 'session-123'},
            {type: 'tool-execution-started', toolName: 'aws_region_audit'},
            {type: 'delta', text: 'Hello'},
            {type: 'complete', sessionId: 'session-123', metadata: {provider: 'bedrock', totalTokens: 42}}
        ]);
    });

    it('loads provider models and status through backend-shaped endpoints', async () => {
        server.use(
            http.get(apiPath('/api/models'), ({request}) => {
                expect(new URL(request.url).searchParams.get('provider')).toBe('ollama');
                return HttpResponse.json({
                    provider: 'ollama',
                    defaultProvider: 'ollama',
                    providers: ['ollama', 'bedrock'],
                    defaultModel: 'llama3:8b',
                    models: ['llama3:8b', 'mistral:7b']
                });
            }),
            http.get(apiPath('/api/models/status'), ({request}) => {
                expect(new URL(request.url).searchParams.get('provider')).toBe('ollama');
                return HttpResponse.json({
                    provider: 'ollama',
                    status: 'ready',
                    message: 'Ollama is reachable and ready.'
                });
            })
        );

        await expect(listAvailableModels('ollama')).resolves.toMatchObject({
            providers: ['ollama', 'bedrock'],
            models: ['llama3:8b', 'mistral:7b']
        });
        await expect(getProviderStatus('ollama')).resolves.toMatchObject({
            status: 'ready'
        });
    });

    it('handles session export and import over HTTP', async () => {
        server.use(
            http.get(apiPath('/api/sessions/session-1/export'), () => new HttpResponse(
                new TextEncoder().encode('{"sessionId":"session-1"}'),
                {
                    status: 200,
                    headers: {
                        'Content-Type': 'application/json',
                        'Content-Disposition': 'attachment; filename="session-1.json"'
                    }
                }
            )),
            http.post(apiPath('/api/sessions/import'), async ({request}) => {
                const formData = await request.formData();
                const file = formData.get('file');
                expect(file).toBeTruthy();
                expect(file?.name).toBe('session.json');
                expect(file?.type).toBe('application/json');
                return HttpResponse.json({
                    sessionId: 'imported-session',
                    title: 'Imported session',
                    summary: 'Imported summary',
                    idChanged: false,
                    messageCount: 2
                });
            })
        );

        const exported = await exportSession('session-1', 'json');
        expect(exported.filename).toBe('session-1.json');
        await expect(readBlobText(exported.blob)).resolves.toBe('{"sessionId":"session-1"}');

        const imported = await importSession(new File(['{}'], 'session.json', {type: 'application/json'}));
        expect(imported.sessionId).toBe('imported-session');
    });

    it('compares rag retrieval targets through the backend endpoint', async () => {
        server.use(
            http.post(apiPath('/api/rag/compare'), async ({request}) => {
                const body = await request.json();
                expect(body).toEqual({
                    question: 'How are sessions persisted?',
                    provider: 'ollama',
                    model: 'llama3:8b',
                    retrievalTargets: ['lexical', 'vector:in-memory']
                });
                return HttpResponse.json({
                    question: 'How are sessions persisted?',
                    results: [
                        {
                            retrievalTarget: 'lexical',
                            success: true,
                            error: null,
                            answer: 'Sessions are stored as local JSON files.',
                            provider: 'ollama',
                            model: 'llama3:8b',
                            sources: [{sourcePath: 'docs/adr/0006.md', title: 'Sessions', excerpt: 'local JSON files', score: 0.91}],
                            ragRetrieval: {retrievalTarget: 'lexical', retrievalMode: 'lexical', retrievalStore: 'in-memory', vectorStore: 'in-memory', topK: 4},
                            ragTiming: {retrievalDurationMs: 2, providerDurationMs: 120, totalDurationMs: 122}
                        },
                        {
                            retrievalTarget: 'vector:in-memory',
                            success: true,
                            error: null,
                            answer: 'Conversation history is persisted in local JSON session files.',
                            provider: 'ollama',
                            model: 'llama3:8b',
                            sources: [{sourcePath: 'docs/adr/0006.md', title: 'Sessions', excerpt: 'session files', score: 0.88}],
                            ragRetrieval: {
                                retrievalTarget: 'vector:in-memory',
                                retrievalMode: 'vector',
                                retrievalStore: 'in-memory-vector',
                                vectorStore: 'in-memory',
                                topK: 4,
                                embeddingProvider: 'ollama',
                                embeddingModel: 'nomic-embed-text'
                            },
                            ragTiming: {retrievalDurationMs: 8, providerDurationMs: 130, totalDurationMs: 138}
                        }
                    ]
                });
            })
        );

        const result = await compareRagRetrievalTargets({
            question: 'How are sessions persisted?',
            provider: 'ollama',
            model: 'llama3:8b',
            retrievalTargets: ['lexical', 'vector:in-memory']
        });

        expect(result.results).toHaveLength(2);
        expect(result.results[0].retrievalTarget).toBe('lexical');
        expect(result.results[0].success).toBe(true);
        expect(result.results[1].ragRetrieval.embeddingModel).toBe('nomic-embed-text');
    });

    it('surfaces non-json rag query proxy failures with the http status', async () => {
        server.use(
            http.post(apiPath('/api/rag/query'), () => new HttpResponse('<html>gateway timeout</html>', {
                status: 504,
                headers: {'Content-Type': 'text/html'}
            }))
        );

        await expect(queryRag({
            question: 'What is Java version?',
            provider: 'ollama',
            model: 'llama3:8b'
        })).rejects.toThrow('Failed to query the RAG workspace. HTTP 504.');
    });

    it('keeps partial rag comparison failures in the successful backend response', async () => {
        server.use(
            http.post(apiPath('/api/rag/compare'), () => HttpResponse.json({
                question: 'How are sessions persisted?',
                results: [
                    {
                        retrievalTarget: 'lexical',
                        success: true,
                        error: null,
                        answer: 'Sessions are stored as local JSON files.',
                        provider: 'ollama',
                        model: 'llama3:8b',
                        sources: [],
                        ragRetrieval: {retrievalTarget: 'lexical'},
                        ragTiming: {totalDurationMs: 50}
                    },
                    {
                        retrievalTarget: 'vector:qdrant',
                        success: false,
                        error: 'Qdrant is not reachable at http://localhost:6333.',
                        answer: null,
                        provider: null,
                        model: null,
                        sources: [],
                        ragRetrieval: {retrievalTarget: 'vector:qdrant'},
                        ragTiming: null
                    }
                ]
            }))
        );

        const result = await compareRagRetrievalTargets({
            question: 'How are sessions persisted?',
            provider: 'ollama',
            model: 'llama3:8b'
        });

        expect(result.results[0].success).toBe(true);
        expect(result.results[1].success).toBe(false);
        expect(result.results[1].error).toMatch(/Qdrant is not reachable/);
    });

    it('surfaces backend rag comparison errors', async () => {
        server.use(
            http.post(apiPath('/api/rag/compare'), () => HttpResponse.json({
                error: 'Unsupported RAG retrieval target: semantic.'
            }, {status: 400}))
        );

        await expect(compareRagRetrievalTargets({
            question: 'How are sessions persisted?',
            provider: 'ollama',
            model: 'llama3:8b',
            retrievalTargets: ['semantic']
        })).rejects.toThrow('Unsupported RAG retrieval target: semantic.');
    });

    it('surfaces artifact and session loading failures', async () => {
        server.use(
            http.get(apiPath('/api/sessions'), () => HttpResponse.json({error: 'Failed to load sessions.'}, {status: 500})),
            http.get(apiPath('/api/artifacts/files'), () => HttpResponse.json({error: 'Failed to list artifact files.'}, {status: 500})),
            http.get(apiPath('/api/artifacts/preview'), () => HttpResponse.json({error: 'Failed to preview artifact.'}, {status: 404}))
        );

        await expect(listSessions()).rejects.toThrow('Failed to load sessions.');
        await expect(listArtifacts('/tmp/audit-1')).rejects.toThrow('Failed to list artifact files.');
        await expect(previewArtifact('/tmp/audit-1/summary.json')).rejects.toThrow('Failed to preview artifact.');
    });
});
