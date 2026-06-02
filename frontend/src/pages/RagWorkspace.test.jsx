/**
 * @fileoverview Integration tests for the RagWorkspace page.
 * Uses MSW to mock backend API responses for RAG status, model listing, and RAG queries.
 */
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {http, HttpResponse, server} from '../test/mswServer';
import RagWorkspace from './RagWorkspace';

describe('RagWorkspace', () => {
    it('loads status, submits a docs query, and renders cited sources from the saved rag session', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory'
            })),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama', 'bedrock'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([])),
            http.post('/api/rag/query', async ({request}) => {
                const body = await request.json();
                expect(body).toMatchObject({
                    question: 'How does provider selection work?',
                    provider: 'ollama',
                    model: 'llama3:8b'
                });
                return HttpResponse.json({
                    answer: 'Provider selection is handled by the provider registry.',
                    provider: 'ollama',
                    model: 'llama3:8b',
                    sessionId: 'rag-session-1',
                    sources: [
                        {
                            sourcePath: 'architecture.md',
                            title: 'Architecture',
                            excerpt: 'The provider registry selects Ollama, Bedrock, or Hugging Face.',
                            score: 0.88
                        }
                    ],
                    metadata: {
                        provider: 'ollama',
                        modelId: 'llama3:8b'
                    }
                });
            }),
            http.get('/api/sessions/rag-session-1', () => HttpResponse.json({
                sessionId: 'rag-session-1',
                title: 'How does provider selection work?',
                summary: 'Provider selection is handled by the provider registry.',
                mode: 'rag',
                model: 'llama3:8b',
                createdAt: '2026-05-27T12:00:00Z',
                updatedAt: '2026-05-27T12:00:05Z',
                messages: [
                    {
                        role: 'user',
                        content: 'How does provider selection work?',
                        tool: null,
                        toolResult: null,
                        metadata: null,
                        ragSources: null,
                        timestamp: '2026-05-27T12:00:00Z'
                    },
                    {
                        role: 'assistant',
                        content: 'Provider selection is handled by the provider registry.',
                        tool: null,
                        toolResult: null,
                        metadata: {
                            provider: 'ollama',
                            modelId: 'llama3:8b'
                        },
                        ragSources: [
                            {
                                sourcePath: 'architecture.md',
                                title: 'Architecture',
                                excerpt: 'The provider registry selects Ollama, Bedrock, or Hugging Face.',
                                score: 0.88
                            }
                        ],
                        timestamp: '2026-05-27T12:00:05Z'
                    }
                ],
                pendingTool: null
            }))
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        expect(await screen.findByRole('heading', {name: /^rag$/i})).toBeInTheDocument();
        expect(screen.getByText('Status')).toBeInTheDocument();
        expect(screen.getByText('ready')).toBeInTheDocument();
        expect(screen.getByText('Retrieval')).toBeInTheDocument();
        expect(screen.getByText('Lexical')).toBeInTheDocument();
        expect(screen.getByText('Store')).toBeInTheDocument();
        expect(screen.getByText('In memory')).toBeInTheDocument();
        await user.type(screen.getByPlaceholderText(/Ask a question about the project docs/i), 'How does provider selection work?');
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));

        expect(await screen.findByText(/Provider selection is handled by the provider registry/i)).toBeInTheDocument();
        expect(screen.getByText('Sources')).toBeInTheDocument();
        expect(screen.getByText('architecture.md')).toBeInTheDocument();
        expect(screen.getByText('How does provider selection work?')).toBeInTheDocument();
    });

    it('shows a disabled state when the backend reports RAG is off', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: false,
                indexed: false,
                corpusRoot: '/repo/docs',
                documentCount: 0,
                chunkCount: 0,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory'
            })),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([]))
        );

        render(<RagWorkspace/>);

        expect(await screen.findByText(/RAG is disabled/i)).toBeInTheDocument();
        expect(screen.getByText(/Enable `rag.enabled=true`/i)).toBeInTheDocument();
    });

    it('shows backend query failures clearly', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory'
            })),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([])),
            http.post('/api/rag/query', () => HttpResponse.json({error: 'RAG backend failed.'}, {status: 503}))
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        await screen.findByRole('heading', {name: /^rag$/i});
        expect(screen.getByText('ready')).toBeInTheDocument();
        await user.type(screen.getByPlaceholderText(/Ask a question about the project docs/i), 'What is MCP?');
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));

        expect(await screen.findByText(/^RAG backend failed\.$/i)).toBeInTheDocument();
        await waitFor(() => {
            expect(screen.queryByText('Sources')).not.toBeInTheDocument();
        });
    });

    it('reopens a saved rag session and renders persisted cited sources without re-querying', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory'
            })),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([
                {
                    sessionId: 'rag-session-1',
                    title: 'How are sessions persisted?',
                    summary: 'Sessions are stored as local JSON files.',
                    mode: 'rag',
                    model: 'llama3:8b',
                    createdAt: '2026-05-27T12:00:00Z',
                    updatedAt: '2026-05-27T12:00:05Z',
                    messageCount: 2
                }
            ])),
            http.get('/api/sessions/rag-session-1', () => HttpResponse.json({
                sessionId: 'rag-session-1',
                title: 'How are sessions persisted?',
                summary: 'Sessions are stored as local JSON files.',
                mode: 'rag',
                model: 'llama3:8b',
                createdAt: '2026-05-27T12:00:00Z',
                updatedAt: '2026-05-27T12:00:05Z',
                messages: [
                    {
                        role: 'user',
                        content: 'How are sessions persisted?',
                        tool: null,
                        toolResult: null,
                        metadata: null,
                        ragSources: null,
                        timestamp: '2026-05-27T12:00:00Z'
                    },
                    {
                        role: 'assistant',
                        content: 'Sessions are stored as local JSON files.',
                        tool: null,
                        toolResult: null,
                        metadata: {
                            provider: 'ollama',
                            modelId: 'llama3:8b'
                        },
                        ragSources: [
                            {
                                sourcePath: 'sessions.md',
                                title: 'Sessions',
                                excerpt: 'Sessions are stored as local JSON files so they can be reopened, exported, and imported.',
                                score: 0.91
                            }
                        ],
                        timestamp: '2026-05-27T12:00:05Z'
                    }
                ],
                pendingTool: null
            }))
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        const sessionTitle = await screen.findByText('How are sessions persisted?');
        await user.click(sessionTitle.closest('button'));

        expect(await screen.findByRole('heading', {name: 'Answer'})).toBeInTheDocument();
        expect(screen.getAllByText('Sessions are stored as local JSON files.').length).toBeGreaterThan(0);
        expect(screen.getByText('sessions.md')).toBeInTheDocument();
    });
});
