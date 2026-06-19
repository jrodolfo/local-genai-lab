/**
 * @fileoverview Integration tests for the RagWorkspace page.
 * Uses MSW to mock backend API responses for RAG status, model listing, and RAG queries.
 */
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {http, HttpResponse, server} from '../test/mswServer';
import RagWorkspace from './RagWorkspace';

describe('RagWorkspace', () => {
    it('shows the session header actions in the same order as the agent sidebar', async () => {
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
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([]))
        );

        render(<RagWorkspace/>);

        const sidebar = await screen.findByRole('complementary');
        const sessionsHeading = within(sidebar).getByRole('heading', {name: 'Sessions'});
        const importButton = within(sidebar).getByRole('button', {name: 'Import JSON'});
        const newSessionButton = within(sidebar).getByRole('button', {name: 'New Session'});

        expect(sessionsHeading.compareDocumentPosition(importButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(importButton.compareDocumentPosition(newSessionButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(within(sidebar).queryByRole('button', {name: 'Import Session'})).not.toBeInTheDocument();
    });

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
                    retrievalTarget: 'lexical'
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
                        retrievalStore: 'in-memory',
                        vectorStore: 'in-memory',
                        retrievalTarget: 'lexical',
                        topK: 3
                    },
                    ragTiming: {
                        retrievalDurationMs: 0,
                        providerDurationMs: 345,
                        totalDurationMs: 400
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
                        ragRetrieval: {
                            retrievalMode: 'lexical',
                            retrievalStore: 'in-memory',
                            vectorStore: 'in-memory',
                            retrievalTarget: 'lexical',
                            topK: 3
                        },
                        ragTiming: {
                            retrievalDurationMs: 0,
                            providerDurationMs: 345,
                            totalDurationMs: 400
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
        const statusRegion = screen.getByRole('region', {name: /rag index status/i});
        expect(within(statusRegion).getByText('Default Retrieval')).toBeInTheDocument();
        expect(within(statusRegion).getByText('Lexical')).toBeInTheDocument();
        expect(within(statusRegion).getByText('Default Store')).toBeInTheDocument();
        expect(within(statusRegion).getByText('In memory')).toBeInTheDocument();
        expect(screen.getByRole('combobox', {name: /retrieval/i})).toHaveValue('lexical');
        expect(screen.getByRole('option', {name: 'Vector - In Memory'})).toBeInTheDocument();
        expect(screen.getByRole('option', {name: 'Vector - Qdrant'})).toBeInTheDocument();
        expect(screen.getByText(/Lexical mode uses keyword search over the local docs/i)).toBeInTheDocument();
        expect(screen.getByText(/Rebuild Index is optional/i)).toBeInTheDocument();
        expect(screen.getByText(/Export Markdown for reading/i)).toBeInTheDocument();
        expect(screen.getByText(/Export JSON for import or backup/i)).toBeInTheDocument();
        const questionInput = screen.getByPlaceholderText(/Ask a question about the project docs/i);
        await user.type(questionInput, 'How does provider selection work?');
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));

        const latestAnswer = await screen.findByRole('region', {name: /latest rag answer/i});
        const latestTurn = await screen.findByRole('region', {name: /latest rag turn/i});
        const queryForm = screen.getByRole('form', {name: /rag query/i});
        const latestQuestionLabel = within(latestTurn).getByText('Question');
        const latestAnswerHeading = within(latestTurn).getByRole('heading', {name: 'Answer'});

        expect(queryForm.nextElementSibling).toBe(latestAnswer);
        expect(within(latestAnswer).getByRole('region', {name: /latest rag turn/i})).toBe(latestTurn);
        expect(latestQuestionLabel.compareDocumentPosition(latestAnswerHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(within(latestTurn).getByText(/Provider selection is handled by the provider registry/i)).toBeInTheDocument();
        expect(questionInput).toHaveValue('');
        const technicalDetailsToggle = screen.getByRole('checkbox', {name: /show technical details/i});
        expect(technicalDetailsToggle).not.toBeChecked();
        expect(within(latestTurn).queryByText('Retrieval mode')).not.toBeInTheDocument();
        await user.click(technicalDetailsToggle);
        expect(technicalDetailsToggle).toBeChecked();
        expect(within(latestTurn).getByText('Retrieval mode')).toBeInTheDocument();
        expect(within(latestTurn).getByText('Lexical')).toBeInTheDocument();
        expect(within(latestTurn).getByText('Retrieval target')).toBeInTheDocument();
        expect(within(latestTurn).getByText('lexical')).toBeInTheDocument();
        expect(within(latestTurn).getByText('Top K')).toBeInTheDocument();
        expect(within(latestTurn).getByText('3')).toBeInTheDocument();
        expect(within(latestTurn).getByText('Retrieval duration')).toBeInTheDocument();
        expect(within(latestTurn).getByText('<1 ms')).toBeInTheDocument();
        expect(within(latestTurn).getByText('Provider duration')).toBeInTheDocument();
        expect(within(latestTurn).getByText('345 ms')).toBeInTheDocument();
        expect(within(latestTurn).getByText('Backend total')).toBeInTheDocument();
        expect(within(latestTurn).getByText('400 ms')).toBeInTheDocument();
        await user.click(technicalDetailsToggle);
        expect(technicalDetailsToggle).not.toBeChecked();
        expect(within(latestTurn).queryByText('Retrieval mode')).not.toBeInTheDocument();
        expect(within(latestTurn).getByText('Sources')).toBeInTheDocument();
        expect(screen.getAllByText(/Provider selection is handled by the provider registry/i)).toHaveLength(1);
        expect(screen.getByText('architecture.md')).toBeInTheDocument();
        expect(within(latestTurn).getByText('How does provider selection work?')).toBeInTheDocument();
        expect(screen.queryByRole('region', {name: /rag conversation history/i})).not.toBeInTheDocument();
    });

    it('explains lazy indexing and refreshes index status after the first successful query', async () => {
        let statusCallCount = 0;
        server.use(
            http.get('/api/rag/status', () => {
                statusCallCount += 1;
                return HttpResponse.json({
                    enabled: true,
                    indexed: statusCallCount > 1,
                    corpusRoot: '/repo/docs',
                    documentCount: statusCallCount > 1 ? 12 : 0,
                    chunkCount: statusCallCount > 1 ? 48 : 0,
                    retrievalMode: 'lexical',
                    retrievalStore: 'in-memory'
                });
            }),
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/sessions', () => HttpResponse.json([])),
            http.post('/api/rag/query', () => HttpResponse.json({
                answer: 'Sessions are stored as local JSON files.',
                provider: 'ollama',
                model: 'llama3:8b',
                sessionId: 'rag-session-1',
                sources: [],
                metadata: {
                    provider: 'ollama',
                    modelId: 'llama3:8b'
                },
                ragRetrieval: {
                    retrievalMode: 'lexical',
                    retrievalStore: 'in-memory',
                    vectorStore: 'in-memory',
                    retrievalTarget: 'lexical',
                    topK: 4
                },
                ragTiming: {
                    retrievalDurationMs: 0,
                    providerDurationMs: 345,
                    totalDurationMs: 400
                }
            })),
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
                        ragSources: [],
                        timestamp: '2026-05-27T12:00:05Z'
                    }
                ],
                pendingTool: null
            }))
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();
        const statusRegion = await screen.findByRole('region', {name: /rag index status/i});

        expect(within(statusRegion).getByText('will index on first question')).toBeInTheDocument();
        expect(within(statusRegion).getAllByText('not loaded yet')).toHaveLength(2);

        await user.type(screen.getByPlaceholderText(/Ask a question about the project docs/i), 'How are sessions persisted?');
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));

        await waitFor(() => {
            expect(within(statusRegion).getByText('ready')).toBeInTheDocument();
        });
        expect(within(statusRegion).getByText('12')).toBeInTheDocument();
        expect(within(statusRegion).getByText('48')).toBeInTheDocument();
        expect(statusCallCount).toBeGreaterThan(1);
    });

    it('sends the selected retrieval target with a rag query', async () => {
        let queryBody = null;
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
            http.post('/api/rag/query', async ({request}) => {
                queryBody = await request.json();
                return HttpResponse.json({
                    answer: 'Provider selection is handled by the provider registry.',
                    provider: 'ollama',
                    model: 'llama3:8b',
                    sessionId: 'rag-session-1',
                    sources: [],
                    metadata: {
                        provider: 'ollama',
                        modelId: 'llama3:8b'
                    },
                    ragRetrieval: {
                        retrievalMode: 'vector',
                        retrievalStore: 'in-memory-vector',
                        vectorStore: 'in-memory',
                        retrievalTarget: 'vector:in-memory',
                        topK: 4,
                        embeddingProvider: 'ollama',
                        embeddingModel: 'nomic-embed-text'
                    },
                    ragTiming: {
                        retrievalDurationMs: 0,
                        providerDurationMs: 345,
                        totalDurationMs: 400
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
                messages: [],
                pendingTool: null
            }))
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        await screen.findByRole('heading', {name: /^rag$/i});
        await user.selectOptions(screen.getByRole('combobox', {name: /retrieval/i}), 'vector:in-memory');
        await user.type(screen.getByPlaceholderText(/Ask a question about the project docs/i), 'How does provider selection work?');
        await user.click(screen.getByRole('button', {name: /Ask docs corpus/i}));

        await waitFor(() => {
            expect(queryBody).toMatchObject({
                question: 'How does provider selection work?',
                retrievalTarget: 'vector:in-memory'
            });
        });
    });

    it('compares retrieval targets without saving a rag answer', async () => {
        let compareBody = null;
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
            http.post('/api/rag/compare', async ({request}) => {
                compareBody = await request.json();
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
                            sources: [
                                {
                                    sourcePath: 'docs/adr/0006-persist-sessions-as-local-json-files.md',
                                    title: 'ADR 0006',
                                    excerpt: 'Persist sessions as local JSON files.',
                                    score: 0.91
                                }
                            ],
                            ragRetrieval: {
                                retrievalMode: 'lexical',
                                retrievalStore: 'in-memory',
                                vectorStore: 'in-memory',
                                retrievalTarget: 'lexical',
                                topK: 4
                            },
                            ragTiming: {
                                retrievalDurationMs: 1,
                                providerDurationMs: 50,
                                totalDurationMs: 51
                            }
                        },
                        {
                            retrievalTarget: 'vector:qdrant',
                            success: false,
                            error: 'Failed to index RAG chunks in Qdrant at http://localhost:6333. Confirm Qdrant is running and reachable, then click Rebuild Index or run Compare Retrieval Targets again. For the local Docker setup, run: docker compose up -d qdrant. Details: connection refused',
                            answer: null,
                            provider: null,
                            model: null,
                            sources: [],
                            ragRetrieval: {
                                retrievalMode: 'vector',
                                retrievalStore: 'in-memory-vector',
                                vectorStore: 'qdrant',
                                retrievalTarget: 'vector:qdrant',
                                topK: 4,
                                embeddingProvider: 'ollama',
                                embeddingModel: 'nomic-embed-text'
                            },
                            ragTiming: null
                        }
                    ]
                });
            })
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        await screen.findByRole('heading', {name: /^rag$/i});
        const questionInput = screen.getByPlaceholderText(/Ask a question about the project docs/i);
        await user.type(questionInput, 'How are sessions persisted?');
        await user.click(screen.getByRole('button', {name: /Compare Retrieval Targets/i}));

        await waitFor(() => {
            expect(compareBody).toMatchObject({
                question: 'How are sessions persisted?',
                provider: 'ollama',
                model: 'llama3:8b'
            });
        });

        const comparison = await screen.findByRole('region', {name: /rag retrieval comparison/i});
        expect(within(comparison).getByText('Retrieval comparison')).toBeInTheDocument();
        expect(within(comparison).getByText(/Diagnostic results only/i)).toBeInTheDocument();
        expect(within(comparison).getByText('Lexical')).toBeInTheDocument();
        expect(within(comparison).getByText('Vector - Qdrant')).toBeInTheDocument();
        expect(within(comparison).getByText('Sessions are stored as local JSON files.')).toBeInTheDocument();
        expect(within(comparison).getByText('docs/adr/0006-persist-sessions-as-local-json-files.md')).toBeInTheDocument();
        expect(within(comparison).getByText('Unavailable')).toBeInTheDocument();
        expect(within(comparison).getByText(/Confirm Qdrant is running and reachable/i)).toBeInTheDocument();
        expect(within(comparison).getByText(/docker compose up -d qdrant/i)).toBeInTheDocument();
        expect(questionInput).toHaveValue('');
        expect(screen.getByRole('heading', {name: /No answer yet/i})).toBeInTheDocument();
        expect(screen.queryByRole('region', {name: /latest rag answer/i})).not.toBeInTheDocument();
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
        expect(screen.getByText(/Vector mode uses semantic search over the local docs/i)).toBeInTheDocument();
        expect(screen.getByText(/uses embeddings to find related content/i)).toBeInTheDocument();
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

        expect(await screen.findByText('Qdrant')).toBeInTheDocument();
        expect(screen.getByText('Reachable')).toBeInTheDocument();
        expect(screen.getByText('Collection')).toBeInTheDocument();
        expect(screen.getByText('Present, 123 points')).toBeInTheDocument();
        expect(screen.getByText('Qdrant collection local_genai_lab_docs is present with 123 points.')).toBeInTheDocument();
        expect(screen.getByLabelText('Qdrant readiness')).toHaveTextContent('Qdrant: ready.');
        expect(screen.getByLabelText('Qdrant readiness')).toHaveTextContent('123 indexed chunks are available for Vector - Qdrant.');
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
        expect(screen.getByLabelText('Qdrant readiness')).toHaveTextContent('Qdrant: index missing.');
        expect(screen.getByLabelText('Qdrant readiness')).toHaveTextContent('Click Rebuild Index before using Vector - Qdrant.');
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

        expect(await screen.findByText('Qdrant')).toBeInTheDocument();
        expect(screen.getByText('Unavailable')).toBeInTheDocument();
        expect(screen.getByText('Not checked')).toBeInTheDocument();
        expect(screen.getByText(/Qdrant is not reachable at http:\/\/localhost:6333\. Start it and rebuild the index\./i)).toBeInTheDocument();
        expect(screen.getByLabelText('Qdrant readiness')).toHaveTextContent('Qdrant: not running.');
        expect(screen.getByLabelText('Qdrant readiness')).toHaveTextContent('Run ./restart.sh, then use Rebuild Index or Compare Retrieval Targets again.');
    });

    it('shows optional qdrant readiness when lexical mode can still use qdrant comparison', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory',
                qdrantUrl: 'http://localhost:6333',
                qdrantCollection: 'local_genai_lab_docs',
                qdrantRequired: false,
                qdrantReachable: false,
                qdrantCollectionExists: null,
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

        expect(await screen.findByRole('combobox', {name: /retrieval/i})).toHaveValue('lexical');
        expect(screen.getByRole('option', {name: 'Vector - Qdrant'})).toBeEnabled();
        expect(screen.getByLabelText('Qdrant readiness')).toHaveTextContent('Qdrant: not running.');
        expect(screen.getByText(/Lexical mode uses keyword search over the local docs/i)).toBeInTheDocument();
    });

    it('shows qdrant not checked when status has qdrant settings without live readiness', async () => {
        server.use(
            http.get('/api/rag/status', () => HttpResponse.json({
                enabled: true,
                indexed: true,
                corpusRoot: '/repo/docs',
                documentCount: 12,
                chunkCount: 48,
                retrievalMode: 'lexical',
                retrievalStore: 'in-memory',
                qdrantUrl: 'http://localhost:6333',
                qdrantCollection: 'local_genai_lab_docs',
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

        expect(await screen.findByLabelText('Qdrant readiness')).toHaveTextContent('Qdrant: not checked.');
        expect(screen.getByLabelText('Qdrant readiness')).toHaveTextContent('Use Compare Retrieval Targets to verify Vector - Qdrant when needed.');
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
        expect(screen.getByText(/2026\/05\/27 \d{2}:\d{2}/)).toBeInTheDocument();
        await user.click(sessionTitle.closest('button'));

        expect(await screen.findByRole('heading', {name: 'Answer'})).toBeInTheDocument();
        expect(screen.getAllByText('Sessions are stored as local JSON files.').length).toBeGreaterThan(0);
        expect(screen.getByText('sessions.md')).toBeInTheDocument();
    });

    it('confirms before deleting a saved rag session', async () => {
        let deleted = false;
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
            http.get('/api/sessions', () => HttpResponse.json(deleted ? [] : [
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
            http.delete('/api/sessions/rag-session-1', () => {
                deleted = true;
                return new HttpResponse(null, {status: 204});
            })
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        expect(await screen.findByText('How are sessions persisted?')).toBeInTheDocument();
        await user.click(screen.getByRole('button', {name: /Delete session How are sessions persisted/i}));
        const dialog = screen.getByRole('alertdialog', {name: /Delete RAG session/i});
        expect(within(dialog).getByText(/This will permanently delete "How are sessions persisted\?"/i)).toBeInTheDocument();
        await user.click(within(dialog).getByRole('button', {name: /Delete Session/i}));

        await waitFor(() => {
            expect(screen.queryByText('How are sessions persisted?')).not.toBeInTheDocument();
        });
    });

    it('keeps a saved rag session when deletion is canceled', async () => {
        let deleteCalled = false;
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
            http.delete('/api/sessions/rag-session-1', () => {
                deleteCalled = true;
                return new HttpResponse(null, {status: 204});
            })
        );

        render(<RagWorkspace/>);
        const user = userEvent.setup();

        expect(await screen.findByText('How are sessions persisted?')).toBeInTheDocument();
        await user.click(screen.getByRole('button', {name: /Delete session How are sessions persisted/i}));
        const dialog = screen.getByRole('alertdialog', {name: /Delete RAG session/i});
        await user.click(within(dialog).getByRole('button', {name: /Keep Session/i}));

        expect(deleteCalled).toBe(false);
        expect(screen.queryByRole('alertdialog', {name: /Delete RAG session/i})).not.toBeInTheDocument();
        expect(screen.getByText('How are sessions persisted?')).toBeInTheDocument();
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

function ragMessage(role, content, timestamp) {
    return {
        role,
        content,
        tool: null,
        toolResult: null,
        metadata: role === 'assistant' ? {provider: 'ollama', modelId: 'llama3:8b'} : null,
        ragSources: role === 'assistant' ? [] : null,
        timestamp
    };
}
