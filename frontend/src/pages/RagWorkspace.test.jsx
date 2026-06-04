/**
 * @fileoverview Integration tests for the RagWorkspace page.
 * Uses MSW to mock backend API responses for RAG status, model listing, and RAG queries.
 */
import {render, screen, waitFor, within} from '@testing-library/react';
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
                retrievalStore: 'in-memory',
                embeddingProvider: 'ollama',
                embeddingModel: 'nomic-embed-text'
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
                    model: 'llama3:8b',
                    retrievalMode: 'lexical',
                    vectorStore: 'in-memory'
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
                    },
                    ragRetrieval: {
                        retrievalMode: 'lexical',
                        vectorStore: 'in-memory',
                        retrievalTarget: 'lexical:in-memory'
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
                        ragRetrieval: {
                            retrievalMode: 'lexical',
                            vectorStore: 'in-memory',
                            retrievalTarget: 'lexical:in-memory'
                        },
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
        expect(screen.getAllByText('Retrieval').length).toBeGreaterThan(0);
        expect(screen.getAllByText('Lexical').length).toBeGreaterThan(0);
        expect(screen.getByText('Store')).toBeInTheDocument();
        expect(screen.getByText('In memory')).toBeInTheDocument();
        expect(screen.getByText(/Backend default is lexical retrieval/i)).toBeInTheDocument();
        expect(screen.getByText(/Use the Retrieval selector to try vector retrieval per question/i)).toBeInTheDocument();
        expect(screen.getByRole('combobox', {name: /retrieval/i})).toHaveValue('lexical:in-memory');
        expect(screen.getByRole('button', {name: /Ask docs corpus/i})).toBeDisabled();
        expect(screen.getByRole('button', {name: /Compare retrieval targets/i})).toBeDisabled();
        expect(screen.getByText(/Enter a question to ask or compare retrieval targets/i)).toBeInTheDocument();
        expect(screen.getByText(/saves one answer using the selected retrieval target/i)).toBeInTheDocument();
        expect(screen.getByText(/runs the same question across available targets without saving results/i)).toBeInTheDocument();
        await user.type(screen.getByPlaceholderText(/Ask a question about the project docs/i), 'How does provider selection work?');
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));

        const latestTurn = await screen.findByRole('region', {name: /latest rag turn/i});
        const queryForm = screen.getByRole('button', {name: /Ask docs corpus/i}).closest('form');
        const latestQuestionHeading = within(latestTurn).getByRole('heading', {name: 'Question'});
        const latestAnswerHeading = within(latestTurn).getByRole('heading', {name: 'Answer'});

        expect(queryForm.compareDocumentPosition(latestTurn) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(latestQuestionHeading.compareDocumentPosition(latestAnswerHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(within(latestTurn).getByText(/Provider selection is handled by the provider registry/i)).toBeInTheDocument();
        expect(within(latestTurn).getByText(/Retrieval: Lexical/i)).toBeInTheDocument();
        expect(within(latestTurn).getByText('Sources')).toBeInTheDocument();
        expect(screen.getAllByText(/Provider selection is handled by the provider registry/i)).toHaveLength(1);
        expect(screen.getByText('architecture.md')).toBeInTheDocument();
        expect(within(latestTurn).getByText('How does provider selection work?')).toBeInTheDocument();
        expect(screen.getByPlaceholderText(/Ask a question about the project docs/i)).toHaveValue('How does provider selection work?');
        expect(screen.queryByRole('region', {name: /rag conversation history/i})).not.toBeInTheDocument();
    });

    it('keeps the question so switching retrieval target and asking again sends the new target', async () => {
        const receivedQueries = [];
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory',
                vectorStore: 'in-memory',
                retrievalTargets: [
                    retrievalTarget('lexical:in-memory', 'Lexical', true, true, 'Ready. Uses lexical retrieval.'),
                    retrievalTarget('vector:in-memory', 'Vector - In Memory', true, true, 'Ready. Uses in-memory vector retrieval.')
                ]
            })),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([])),
            http.post('/api/rag/query', async ({request}) => {
                const body = await request.json();
                receivedQueries.push(body);
                const sessionId = `rag-session-${receivedQueries.length}`;
                return HttpResponse.json({
                    answer: `${body.retrievalMode} answer`,
                    provider: 'ollama',
                    model: 'llama3:8b',
                    sessionId,
                    sources: [
                        {
                            sourcePath: 'sessions.md',
                            title: 'Sessions',
                            excerpt: 'Sessions are stored as local JSON files.',
                            score: 0.9
                        }
                    ],
                    metadata: {
                        provider: 'ollama',
                        modelId: 'llama3:8b'
                    },
                    ragRetrieval: {
                        retrievalMode: body.retrievalMode,
                        vectorStore: body.vectorStore,
                        retrievalTarget: `${body.retrievalMode}:${body.vectorStore}`
                    }
                });
            }),
            http.get('/api/sessions/:sessionId', ({params}) => HttpResponse.json({
                sessionId: params.sessionId,
                title: 'How are sessions persisted?',
                summary: 'Sessions are stored as local JSON files.',
                mode: 'rag',
                model: 'llama3:8b',
                createdAt: '2026-05-27T12:00:00Z',
                updatedAt: '2026-05-27T12:00:05Z',
                messages: [
                    ragMessage('user', 'How are sessions persisted?', '2026-05-27T12:00:00Z'),
                    ragMessage('assistant', 'Sessions are stored as local JSON files.', '2026-05-27T12:00:05Z')
                ],
                pendingTool: null
            }))
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        const questionField = await screen.findByPlaceholderText(/Ask a question about the project docs/i);
        await user.type(questionField, 'How are sessions persisted?');
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));
        await waitFor(() => expect(receivedQueries).toHaveLength(1));

        expect(questionField).toHaveValue('How are sessions persisted?');
        await user.selectOptions(screen.getByRole('combobox', {name: /retrieval/i}), 'vector:in-memory');
        expect(screen.getByText(/Rebuild Index applies to the selected retrieval target/i)).toBeInTheDocument();
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));

        await waitFor(() => expect(receivedQueries).toHaveLength(2));
        expect(receivedQueries[0]).toMatchObject({
            question: 'How are sessions persisted?',
            retrievalMode: 'lexical',
            vectorStore: 'in-memory'
        });
        expect(receivedQueries[1]).toMatchObject({
            question: 'How are sessions persisted?',
            retrievalMode: 'vector',
            vectorStore: 'in-memory'
        });
    });

    it('sends selected vector retrieval options with rebuild and query requests', async () => {
        const receivedQueries = [];
        const receivedIndexes = [];
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory',
                vectorStore: 'in-memory',
                embeddingProvider: 'ollama',
                embeddingModel: 'nomic-embed-text'
            })),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([])),
            http.post('/api/rag/index', async ({request}) => {
                receivedIndexes.push(await request.json());
                return HttpResponse.json({
                    corpusRoot: '/repo/docs',
                    documentCount: 12,
                    chunkCount: 48,
                    retrievalMode: 'vector'
                });
            }),
            http.post('/api/rag/query', async ({request}) => {
                receivedQueries.push(await request.json());
                return HttpResponse.json({
                    answer: 'Sessions are stored as local JSON files.',
                    provider: 'ollama',
                    model: 'llama3:8b',
                    sessionId: 'rag-session-2',
                    sources: [
                        {
                            sourcePath: 'sessions.md',
                            title: 'Sessions',
                            excerpt: 'Sessions are stored as local JSON files.',
                            score: 0.9
                        }
                    ],
                    metadata: {
                        provider: 'ollama',
                        modelId: 'llama3:8b'
                    },
                    ragRetrieval: {
                        retrievalMode: 'vector',
                        vectorStore: 'in-memory',
                        retrievalTarget: 'vector:in-memory'
                    }
                });
            }),
            http.get('/api/sessions/rag-session-2', () => HttpResponse.json({
                sessionId: 'rag-session-2',
                title: 'How are sessions persisted?',
                summary: 'Sessions are stored as local JSON files.',
                mode: 'rag',
                model: 'llama3:8b',
                createdAt: '2026-05-27T12:00:00Z',
                updatedAt: '2026-05-27T12:00:05Z',
                messages: [
                    ragMessage('user', 'How are sessions persisted?', '2026-05-27T12:00:00Z'),
                    ragMessage(
                        'assistant',
                        'Sessions are stored as local JSON files.',
                        '2026-05-27T12:00:05Z',
                        {retrievalMode: 'vector', vectorStore: 'in-memory', retrievalTarget: 'vector:in-memory'}
                    )
                ],
                pendingTool: null
            }))
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        const retrievalSelect = await screen.findByRole('combobox', {name: /retrieval/i});
        await user.selectOptions(retrievalSelect, 'vector:in-memory');
        expect(screen.getByText(/Uses Ollama embeddings and an in-memory vector index/i)).toBeInTheDocument();

        await user.click(screen.getByRole('button', {name: /Rebuild index/i}));
        await user.type(screen.getByPlaceholderText(/Ask a question about the project docs/i), 'How are sessions persisted?');
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));

        await waitFor(() => {
            expect(receivedIndexes).toHaveLength(1);
            expect(receivedQueries).toHaveLength(1);
        });
        expect(receivedIndexes[0]).toMatchObject({retrievalMode: 'vector', vectorStore: 'in-memory'});
        expect(receivedQueries[0]).toMatchObject({
            question: 'How are sessions persisted?',
            retrievalMode: 'vector',
            vectorStore: 'in-memory'
        });
        expect(screen.getByText(/Retrieval: Vector - In Memory/i)).toBeInTheDocument();
    });

    it('compares available retrieval targets without saving conversation turns', async () => {
        const receivedQueries = [];
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory',
                vectorStore: 'in-memory',
                retrievalTargets: [
                    retrievalTarget('lexical:in-memory', 'Lexical', true, true, 'Ready. Uses lexical retrieval.'),
                    retrievalTarget('vector:in-memory', 'Vector - In Memory', true, true, 'Ready. Uses in-memory vector retrieval.'),
                    retrievalTarget('vector:qdrant', 'Vector - Qdrant Unavailable', false, false, 'Qdrant is unavailable.')
                ]
            })),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([])),
            http.post('/api/rag/query', async ({request}) => {
                const body = await request.json();
                receivedQueries.push(body);
                return HttpResponse.json({
                    answer: `${body.retrievalMode} answer`,
                    provider: 'ollama',
                    model: 'llama3:8b',
                    sessionId: null,
                    sources: [
                        {
                            sourcePath: 'sessions.md',
                            title: 'Sessions',
                            excerpt: 'Sessions are stored as local JSON files.',
                            score: body.retrievalMode === 'lexical' ? 0.9 : 0.8
                        }
                    ],
                    metadata: {
                        provider: 'ollama',
                        modelId: 'llama3:8b'
                    },
                    ragRetrieval: {
                        retrievalMode: body.retrievalMode,
                        vectorStore: body.vectorStore,
                        retrievalTarget: `${body.retrievalMode}:${body.vectorStore}`
                    }
                });
            })
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        await user.type(await screen.findByPlaceholderText(/Ask a question about the project docs/i), 'How are sessions persisted?');
        await user.click(screen.getByRole('button', {name: /Compare retrieval targets/i}));

        const comparison = await screen.findByRole('region', {name: /rag retrieval comparison/i});
        expect(within(comparison).getByText('Lexical')).toBeInTheDocument();
        expect(within(comparison).getByText('Vector - In Memory')).toBeInTheDocument();
        expect(within(comparison).queryByText('Vector - Qdrant Unavailable')).not.toBeInTheDocument();
        expect(within(comparison).getByText('lexical answer')).toBeInTheDocument();
        expect(within(comparison).getByText('vector answer')).toBeInTheDocument();

        await waitFor(() => expect(receivedQueries).toHaveLength(2));
        expect(receivedQueries).toEqual([
            expect.objectContaining({retrievalMode: 'lexical', vectorStore: 'in-memory', persist: false}),
            expect.objectContaining({retrievalMode: 'vector', vectorStore: 'in-memory', persist: false})
        ]);
        expect(screen.queryByRole('region', {name: /latest rag turn/i})).not.toBeInTheDocument();
    });

    it('keeps successful comparison cards visible when one retrieval target fails', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory',
                vectorStore: 'in-memory',
                retrievalTargets: [
                    retrievalTarget('lexical:in-memory', 'Lexical', true, true, 'Ready. Uses lexical retrieval.'),
                    retrievalTarget('vector:in-memory', 'Vector - In Memory', true, true, 'Ready. Uses in-memory vector retrieval.')
                ]
            })),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([])),
            http.post('/api/rag/query', async ({request}) => {
                const body = await request.json();
                if (body.retrievalMode === 'vector') {
                    return HttpResponse.json({error: 'Vector index is unavailable.'}, {status: 400});
                }
                return HttpResponse.json({
                    answer: 'Lexical answer is available.',
                    provider: 'ollama',
                    model: 'llama3:8b',
                    sessionId: null,
                    sources: [
                        {
                            sourcePath: 'sessions.md',
                            title: 'Sessions',
                            excerpt: 'Sessions are stored as local JSON files.',
                            score: 0.9
                        }
                    ],
                    metadata: {
                        provider: 'ollama',
                        modelId: 'llama3:8b'
                    },
                    ragRetrieval: {
                        retrievalMode: 'lexical',
                        vectorStore: 'in-memory',
                        retrievalTarget: 'lexical:in-memory'
                    }
                });
            })
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        await user.type(await screen.findByPlaceholderText(/Ask a question about the project docs/i), 'How are sessions persisted?');
        await user.click(screen.getByRole('button', {name: /Compare retrieval targets/i}));

        const comparison = await screen.findByRole('region', {name: /rag retrieval comparison/i});
        expect(within(comparison).getByText('Lexical answer is available.')).toBeInTheDocument();
        expect(within(comparison).getByText('Vector index is unavailable.')).toBeInTheDocument();
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

    it('shows vector retrieval mode embedding metadata and restart guidance', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'vector',
                retrievalStore: 'in-memory-vector',
                embeddingProvider: 'ollama',
                embeddingModel: 'nomic-embed-text'
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

        expect(await screen.findByText('Vector')).toBeInTheDocument();
        expect(screen.getByText('In memory vector')).toBeInTheDocument();
        expect(screen.getByText('Embedding')).toBeInTheDocument();
        expect(screen.getByText('Ollama / nomic-embed-text')).toBeInTheDocument();
        expect(screen.getByText(/Backend default is vector retrieval/i)).toBeInTheDocument();
        expect(screen.getByText(/Use the Retrieval selector to override per question/i)).toBeInTheDocument();
    });

    it('shows qdrant readiness when qdrant vector store is reachable', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'vector',
                retrievalStore: 'in-memory-vector',
                vectorStore: 'qdrant',
                qdrantUrl: 'http://localhost:6333',
                qdrantCollection: 'local_genai_lab_docs',
                qdrantRequired: true,
                qdrantReachable: true,
                qdrantCollectionExists: true,
                qdrantPointCount: 123,
                qdrantStatusMessage: 'Qdrant collection local_genai_lab_docs is present with 123 points.',
                embeddingProvider: 'ollama',
                embeddingModel: 'nomic-embed-text',
                retrievalTargets: [
                    retrievalTarget('lexical:in-memory', 'Lexical', true, true, 'Ready. Uses the zero-dependency lexical index for this request.'),
                    retrievalTarget('vector:in-memory', 'Vector - In Memory', true, true, 'Ready. Uses Ollama embeddings and an in-memory vector index.'),
                    retrievalTarget('vector:qdrant', 'Vector - Qdrant', true, true, 'Ready. Qdrant collection local_genai_lab_docs has 123 points.', 123)
                ]
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

        expect(await screen.findByText('Qdrant')).toBeInTheDocument();
        expect(screen.getByText('Reachable')).toBeInTheDocument();
        expect(screen.getByText('Collection')).toBeInTheDocument();
        expect(screen.getByText('Present, 123 points')).toBeInTheDocument();
        expect(screen.getByText('Qdrant collection local_genai_lab_docs is present with 123 points.')).toBeInTheDocument();
        expect(screen.getByRole('option', {name: 'Vector - Qdrant'})).toBeEnabled();
        expect(screen.getByText(/Ready\. Qdrant collection local_genai_lab_docs has 123 points\./i)).toBeInTheDocument();
    });

    it('shows qdrant missing collection guidance when rebuild is needed', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'vector',
                retrievalStore: 'in-memory-vector',
                vectorStore: 'qdrant',
                qdrantUrl: 'http://localhost:6333',
                qdrantCollection: 'local_genai_lab_docs',
                qdrantRequired: true,
                qdrantReachable: true,
                qdrantCollectionExists: false,
                qdrantStatusMessage: 'Qdrant collection local_genai_lab_docs is missing. Rebuild the index.',
                embeddingProvider: 'ollama',
                embeddingModel: 'nomic-embed-text'
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

        expect(await screen.findByText('Collection')).toBeInTheDocument();
        expect(screen.getByText('Missing')).toBeInTheDocument();
        expect(screen.getByText('Qdrant collection local_genai_lab_docs is missing. Rebuild the index.')).toBeInTheDocument();
    });

    it('shows qdrant unavailable guidance only when qdrant is required', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'vector',
                retrievalStore: 'in-memory-vector',
                vectorStore: 'qdrant',
                qdrantUrl: 'http://localhost:6333',
                qdrantCollection: 'local_genai_lab_docs',
                qdrantRequired: true,
                qdrantReachable: false,
                qdrantCollectionExists: null,
                qdrantStatusMessage: 'Qdrant is not reachable at http://localhost:6333.',
                embeddingProvider: 'ollama',
                embeddingModel: 'nomic-embed-text',
                retrievalTargets: [
                    retrievalTarget('lexical:in-memory', 'Lexical', true, true, 'Ready. Uses the zero-dependency lexical index for this request.'),
                    retrievalTarget('vector:in-memory', 'Vector - In Memory', true, true, 'Ready. Uses Ollama embeddings and an in-memory vector index.'),
                    retrievalTarget('vector:qdrant', 'Vector - Qdrant Unavailable', false, false, 'Qdrant is not reachable at http://localhost:6333. Start Qdrant before selecting this target.')
                ]
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

        expect(await screen.findByText('Qdrant')).toBeInTheDocument();
        expect(screen.getByText('Unavailable')).toBeInTheDocument();
        expect(screen.getByText('Not checked')).toBeInTheDocument();
        expect(screen.getByText(/Qdrant is not reachable at http:\/\/localhost:6333\. Start it and rebuild the index\./i)).toBeInTheDocument();
        expect(screen.getByRole('option', {name: 'Vector - Qdrant Unavailable'})).toBeDisabled();
        expect(screen.getAllByText(/Qdrant is not reachable at http:\/\/localhost:6333\. Start Qdrant before selecting this target\./i).length).toBeGreaterThan(0);
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
                        ragRetrieval: {
                            retrievalMode: 'vector',
                            vectorStore: 'qdrant',
                            retrievalTarget: 'vector:qdrant'
                        },
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
        expect(screen.getByText(/Retrieval: Vector - Qdrant/i)).toBeInTheDocument();
        expect(screen.getByText('sessions.md')).toBeInTheDocument();
    });

    it('renders rag conversation turns newest first without separating questions from answers', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'vector',
                retrievalStore: 'in-memory-vector'
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
                    sessionId: 'rag-session-3',
                    title: 'RAG comparison',
                    summary: 'Three RAG turns.',
                    mode: 'rag',
                    model: 'llama3:8b',
                    createdAt: '2026-06-03T10:00:00Z',
                    updatedAt: '2026-06-03T10:03:00Z',
                    messageCount: 6
                }
            ])),
            http.get('/api/sessions/rag-session-3', () => HttpResponse.json({
                sessionId: 'rag-session-3',
                title: 'RAG comparison',
                summary: 'Three RAG turns.',
                mode: 'rag',
                model: 'llama3:8b',
                createdAt: '2026-06-03T10:00:00Z',
                updatedAt: '2026-06-03T10:03:00Z',
                messages: [
                    ragMessage('user', 'How are sessions persisted?', '2026-06-03T10:00:00Z'),
                    ragMessage('assistant', 'Sessions are persisted as local JSON files.', '2026-06-03T10:00:10Z'),
                    ragMessage('user', 'Where does conversation history live?', '2026-06-03T10:01:00Z'),
                    ragMessage('assistant', 'Conversation history lives in the local JSON session files.', '2026-06-03T10:01:10Z'),
                    ragMessage('user', 'What should I check when vector RAG is not working?', '2026-06-03T10:02:00Z'),
                    ragMessage('assistant', 'Check Ollama, the embedding model, and the rebuilt vector index.', '2026-06-03T10:02:10Z')
                ],
                pendingTool: null
            }))
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        const sessionTitle = await screen.findByText('RAG comparison');
        await user.click(sessionTitle.closest('button'));

        const question3 = await screen.findByText('What should I check when vector RAG is not working?');
        const answer3 = screen.getByText('Check Ollama, the embedding model, and the rebuilt vector index.');
        const question2 = screen.getByText('Where does conversation history live?');
        const answer2 = screen.getByText('Conversation history lives in the local JSON session files.');
        const question1 = screen.getByText('How are sessions persisted?');
        const answer1 = screen.getByText('Sessions are persisted as local JSON files.');

        expect(question3.compareDocumentPosition(answer3) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(answer3.compareDocumentPosition(question2) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(question2.compareDocumentPosition(answer2) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(answer2.compareDocumentPosition(question1) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(question1.compareDocumentPosition(answer1) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });
});

function ragMessage(role, content, timestamp, ragRetrieval = null) {
    return {
        role,
        content,
        tool: null,
        toolResult: null,
        metadata: role === 'assistant' ? {provider: 'ollama', modelId: 'llama3:8b'} : null,
        ragSources: role === 'assistant' ? [] : null,
        ragRetrieval,
        timestamp
    };
}

function retrievalTarget(value, label, available, ready, message, pointCount = null) {
    const [retrievalMode, vectorStore] = value.split(':');
    return {
        value,
        label,
        retrievalMode,
        vectorStore,
        available,
        ready,
        message,
        pointCount
    };
}
