import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Home from './Home';
import {http, HttpResponse, server, sseEventChunk, sseStreamResponse} from '../test/mswServer';
import {defaultRuntimeHandlers} from '../test/mswHandlers';

async function waitForSelectValue(roleOptions, value) {
    const select = await screen.findByRole('combobox', roleOptions);
    await waitFor(() => {
        expect(select).toHaveValue(value);
    });
    return select;
}

describe('Home integration', () => {
    beforeEach(() => {
        window.localStorage.clear();
        Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
            configurable: true,
            value: vi.fn()
        });
        Object.defineProperty(navigator, 'clipboard', {
            configurable: true,
            value: {
                writeText: vi.fn().mockResolvedValue(undefined)
            }
        });
    });

    it('loads models and sessions from the backend and completes a non-streaming chat request', async () => {
        server.use(
            ...defaultRuntimeHandlers(),
            http.post('/api/chat', async ({request}) => {
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
                    tool: {
                        used: true,
                        name: 'aws_region_audit',
                        status: 'success',
                        summary: 'AWS audit completed.'
                    },
                    toolResult: {
                        type: 'audit_summary',
                        successCount: 10,
                        failureCount: 0,
                        skippedCount: 1
                    }
                });
            })
        );

        render(<Home/>);
        const user = userEvent.setup();

        await waitForSelectValue({name: /model/i}, 'llama3:8b');
        await user.click(screen.getByLabelText(/Streaming/i));
        await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
        await user.click(screen.getByRole('button', {name: /send/i}));

        expect(await screen.findByText('Audit complete.')).toBeInTheDocument();
        expect(screen.getByText('AWS audit result')).toBeInTheDocument();
        expect(screen.getByText(/^aws_region_audit$/i)).toBeInTheDocument();
    });

    it('shows provider status and backend-driven provider messaging during initial load', async () => {
        server.use(
            ...defaultRuntimeHandlers({
                status: {
                    provider: 'ollama',
                    status: 'ready',
                    message: 'Ollama is reachable and ready.',
                    refreshedAt: '2026-04-19T00:00:00Z'
                }
            })
        );

        render(<Home/>);

        await waitForSelectValue({name: /chat provider/i}, 'ollama');
        expect(screen.getByRole('heading', {name: /^agent$/i})).toBeInTheDocument();
        expect(await screen.findByText(/Ollama status: ready/i)).toBeInTheDocument();
        expect(screen.getByText(/Last checked:/i)).toBeInTheDocument();
    });

    it('shows a backend error when a non-streaming chat request fails', async () => {
        server.use(
            ...defaultRuntimeHandlers(),
            http.post('/api/chat', () => HttpResponse.json({error: 'Provider request failed.'}, {status: 503}))
        );

        render(<Home/>);
        const user = userEvent.setup();

        await user.click(await screen.findByLabelText(/Streaming/i));
        await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
        await user.click(screen.getByRole('button', {name: /send/i}));

        expect(await screen.findByText(/^Provider request failed\.$/i)).toBeInTheDocument();
        expect(screen.queryByText('AWS audit result')).not.toBeInTheDocument();
    });

    it('streams a successful chat response through backend-shaped SSE events', async () => {
        server.use(
            ...defaultRuntimeHandlers(),
            http.post('/api/chat/stream', async ({request}) => {
                const body = await request.json();
                expect(body).toMatchObject({
                    message: 'stream hello',
                    provider: 'ollama',
                    model: 'llama3:8b'
                });
                return sseStreamResponse(new ReadableStream({
                    start(controller) {
                        const encoder = new TextEncoder();
                        controller.enqueue(encoder.encode(sseEventChunk({
                            type: 'start',
                            sessionId: 'session-stream-1'
                        })));
                        setTimeout(() => {
                            controller.enqueue(encoder.encode(sseEventChunk({type: 'delta', text: 'Hello '})));
                        }, 10);
                        setTimeout(() => {
                            controller.enqueue(encoder.encode(sseEventChunk({
                                type: 'delta',
                                text: 'streaming world.'
                            })));
                        }, 20);
                        setTimeout(() => {
                            controller.enqueue(encoder.encode(sseEventChunk({
                                type: 'complete',
                                sessionId: 'session-stream-1',
                                metadata: {provider: 'ollama', modelId: 'llama3:8b'}
                            })));
                            controller.close();
                        }, 30);
                    }
                }));
            })
        );

        render(<Home/>);
        const user = userEvent.setup();

        await waitForSelectValue({name: /model/i}, 'llama3:8b');
        await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'stream hello');
        await user.click(screen.getByRole('button', {name: /send/i}));

        expect(await screen.findByText('Hello streaming world.')).toBeInTheDocument();
        await waitFor(() => {
            expect(screen.getByText(/Ollama\s*·\s*llama3:8b/i)).toBeInTheDocument();
        });
    });

    it('shows explicit backend tool phases during a tool-assisted streaming request', async () => {
        server.use(
            ...defaultRuntimeHandlers(),
            http.post('/api/chat/stream', () => sseStreamResponse(new ReadableStream({
                start(controller) {
                    const encoder = new TextEncoder();
                    setTimeout(() => {
                        controller.enqueue(encoder.encode(sseEventChunk({
                            type: 'tool-decision-started',
                            toolName: 'aws_region_audit'
                        })));
                    }, 25);
                    setTimeout(() => {
                        controller.enqueue(encoder.encode(sseEventChunk({
                            type: 'tool-execution-started',
                            toolName: 'aws_region_audit'
                        })));
                    }, 175);
                    setTimeout(() => {
                        controller.enqueue(encoder.encode(sseEventChunk({
                            type: 'tool-execution-completed',
                            toolName: 'aws_region_audit'
                        })));
                    }, 325);
                    setTimeout(() => {
                        controller.enqueue(encoder.encode(sseEventChunk({
                            type: 'answer-generation-started',
                            toolName: 'aws_region_audit'
                        })));
                        controller.enqueue(encoder.encode(sseEventChunk({
                            type: 'start',
                            sessionId: 'session-tool-1',
                            tool: {
                                used: true,
                                name: 'aws_region_audit',
                                status: 'success',
                                summary: 'AWS audit completed.'
                            }
                        })));
                    }, 475);
                    setTimeout(() => {
                        controller.enqueue(encoder.encode(sseEventChunk({type: 'delta', text: 'Audit complete.'})));
                    }, 625);
                    setTimeout(() => {
                        controller.enqueue(encoder.encode(sseEventChunk({
                            type: 'complete',
                            sessionId: 'session-tool-1',
                            tool: {
                                used: true,
                                name: 'aws_region_audit',
                                status: 'success',
                                summary: 'AWS audit completed.'
                            },
                            toolResult: {
                                type: 'audit_summary',
                                successCount: 10,
                                failureCount: 0,
                                skippedCount: 1
                            },
                            metadata: {provider: 'ollama', modelId: 'llama3:8b'}
                        })));
                        controller.close();
                    }, 775);
                }
            })))
        );

        render(<Home/>);
        const user = userEvent.setup();

        await waitForSelectValue({name: /model/i}, 'llama3:8b');
        await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
        await user.click(screen.getByRole('button', {name: /send/i}));

        expect(await screen.findByText(/Checking whether a tool is needed/i)).toBeInTheDocument();
        expect(await screen.findByText(/Running tool: aws_region_audit/i)).toBeInTheDocument();
        expect(await screen.findByText(/Preparing the final answer from tool results/i)).toBeInTheDocument();
        expect(await screen.findByText('Audit complete.')).toBeInTheDocument();
        expect(screen.getByText(/^aws_region_audit$/i)).toBeInTheDocument();
        await waitFor(() => {
            expect(screen.queryByText(/Preparing the final answer from tool results/i)).not.toBeInTheDocument();
        });
    });

    it('shows a backend error when a streaming chat request fails', async () => {
        server.use(
            ...defaultRuntimeHandlers(),
            http.post('/api/chat/stream', () => HttpResponse.json({error: 'Stream backend failed.'}, {status: 503}))
        );

        render(<Home/>);
        const user = userEvent.setup();

        await waitForSelectValue({name: /model/i}, 'llama3:8b');
        await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'stream fail');
        await user.click(screen.getByRole('button', {name: /send/i}));

        expect(await screen.findByText(/^Stream backend failed\.$/i)).toBeInTheDocument();
        expect(screen.getByText(/Error calling backend\/Ollama\. Check backend logs\./i)).toBeInTheDocument();
    });

    it('allows canceling a streaming request and marks the partial reply as canceled', async () => {
        server.use(
            ...defaultRuntimeHandlers(),
            http.post('/api/chat/stream', ({request}) => {
                const encoder = new TextEncoder();
                return sseStreamResponse(new ReadableStream({
                    start(controller) {
                        controller.enqueue(encoder.encode(sseEventChunk({
                            type: 'start',
                            sessionId: 'session-cancel-1',
                            pendingTool: null,
                            tool: null,
                            toolResult: null,
                            metadata: null
                        })));
                        controller.enqueue(encoder.encode(sseEventChunk({type: 'delta', text: 'Partial answer'})));
                        request.signal.addEventListener('abort', () => {
                            controller.error(new DOMException('The operation was aborted.', 'AbortError'));
                        });
                    }
                }));
            })
        );

        render(<Home/>);
        const user = userEvent.setup();

        await waitForSelectValue({name: /model/i}, 'llama3:8b');
        await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'cancel this stream');
        await user.click(screen.getByRole('button', {name: /send/i}));

        expect(await screen.findByText('Partial answer')).toBeInTheDocument();
        await user.click(screen.getByRole('button', {name: /cancel/i}));

        expect(await screen.findByText(/\[Response canceled\.\]/i)).toBeInTheDocument();
        expect(await screen.findByText(/Request canceled\./i)).toBeInTheDocument();
    });

    it('reopens a saved session and previews an artifact through the backend endpoints', async () => {
        server.use(
            ...defaultRuntimeHandlers({
                sessions: [
                    {
                        sessionId: 'session-1',
                        title: 'run aws audit',
                        summary: 'Audit complete.',
                        model: 'llama3:8b',
                        createdAt: '2026-04-10T10:00:00Z',
                        updatedAt: '2026-04-10T10:01:00Z',
                        messageCount: 2
                    }
                ]
            }),
            http.get('/api/sessions/session-1', () => HttpResponse.json({
                sessionId: 'session-1',
                title: 'run aws audit',
                summary: 'Audit complete.',
                model: 'llama3:8b',
                createdAt: '2026-04-10T10:00:00Z',
                updatedAt: '2026-04-10T10:01:00Z',
                pendingTool: null,
                messages: [
                    {role: 'user', content: 'run aws audit', tool: null, timestamp: '2026-04-10T10:00:00Z'},
                    {
                        role: 'assistant',
                        content: 'Audit complete.',
                        tool: {
                            used: true,
                            name: 'aws_region_audit',
                            status: 'success',
                            summary: 'AWS audit completed.'
                        },
                        toolResult: {
                            type: 'audit_summary',
                            runDir: '/tmp/audit-1',
                            summaryPath: '/tmp/audit-1/summary.json',
                            reportPath: '/tmp/audit-1/report.txt',
                            successCount: 10,
                            failureCount: 0,
                            skippedCount: 1
                        },
                        metadata: {provider: 'bedrock', modelId: 'us.amazon.nova-pro-v1:0'},
                        timestamp: '2026-04-10T10:01:00Z'
                    }
                ]
            })),
            http.get('/api/artifacts/preview', ({request}) => {
                expect(new URL(request.url).searchParams.get('path')).toBe('/tmp/audit-1/summary.json');
                return HttpResponse.json({
                    fileName: 'summary.json',
                    path: '/tmp/audit-1/summary.json',
                    relativePath: 'audit/aws-audit-2026-04-10/summary.json',
                    contentType: 'application/json',
                    size: 42,
                    truncated: false,
                    content: '{"success_count":10,"failure_count":0}'
                });
            })
        );

        render(<Home/>);
        const user = userEvent.setup();

        const sessionTitle = await screen.findByText('run aws audit');
        await user.click(sessionTitle.closest('button'));
        await user.click(await screen.findByRole('button', {name: /open summary/i}));

        expect(await screen.findByText('Summary preview')).toBeInTheDocument();
        expect(screen.getByText(/Relative path: audit\/aws-audit-2026-04-10\/summary\.json/i)).toBeInTheDocument();
    });

    it('shows a clear message when a restored-session artifact preview is no longer available', async () => {
        server.use(
            ...defaultRuntimeHandlers({
                sessions: [
                    {
                        sessionId: 'session-1',
                        title: 'run aws audit',
                        summary: 'Audit complete.',
                        model: 'llama3:8b',
                        createdAt: '2026-04-10T10:00:00Z',
                        updatedAt: '2026-04-10T10:01:00Z',
                        messageCount: 2
                    }
                ]
            }),
            http.get('/api/sessions/session-1', () => HttpResponse.json({
                sessionId: 'session-1',
                title: 'run aws audit',
                summary: 'Audit complete.',
                model: 'llama3:8b',
                createdAt: '2026-04-10T10:00:00Z',
                updatedAt: '2026-04-10T10:01:00Z',
                pendingTool: null,
                messages: [
                    {role: 'user', content: 'run aws audit', tool: null, timestamp: '2026-04-10T10:00:00Z'},
                    {
                        role: 'assistant',
                        content: 'Audit complete.',
                        tool: {
                            used: true,
                            name: 'aws_region_audit',
                            status: 'success',
                            summary: 'AWS audit completed.'
                        },
                        toolResult: {
                            type: 'audit_summary',
                            runDir: '/tmp/audit-1',
                            summaryPath: '/tmp/audit-1/summary.json',
                            reportPath: '/tmp/audit-1/report.txt',
                            successCount: 10,
                            failureCount: 0,
                            skippedCount: 1
                        },
                        metadata: {provider: 'bedrock', modelId: 'us.amazon.nova-pro-v1:0'},
                        timestamp: '2026-04-10T10:01:00Z'
                    }
                ]
            })),
            http.get('/api/artifacts/preview', () => HttpResponse.json({error: 'Artifact not found.'}, {status: 404}))
        );

        render(<Home/>);
        const user = userEvent.setup();

        const sessionTitle = await screen.findByText('run aws audit');
        await user.click(sessionTitle.closest('button'));
        await user.click(await screen.findByRole('button', {name: /open summary/i}));

        expect(await screen.findByText('Summary preview')).toBeInTheDocument();
        expect(screen.getByText(/This artifact is no longer available on disk\./i)).toBeInTheDocument();
        expect(screen.getByText(/Artifact not found\./i)).toBeInTheDocument();
    });

    it('shows a clear message when a restored-session run directory is no longer available', async () => {
        server.use(
            ...defaultRuntimeHandlers({
                sessions: [
                    {
                        sessionId: 'session-1',
                        title: 'run aws audit',
                        summary: 'Audit complete.',
                        model: 'llama3:8b',
                        createdAt: '2026-04-10T10:00:00Z',
                        updatedAt: '2026-04-10T10:01:00Z',
                        messageCount: 2
                    }
                ]
            }),
            http.get('/api/sessions/session-1', () => HttpResponse.json({
                sessionId: 'session-1',
                title: 'run aws audit',
                summary: 'Audit complete.',
                model: 'llama3:8b',
                createdAt: '2026-04-10T10:00:00Z',
                updatedAt: '2026-04-10T10:01:00Z',
                pendingTool: null,
                messages: [
                    {role: 'user', content: 'run aws audit', tool: null, timestamp: '2026-04-10T10:00:00Z'},
                    {
                        role: 'assistant',
                        content: 'Audit complete.',
                        tool: {
                            used: true,
                            name: 'aws_region_audit',
                            status: 'success',
                            summary: 'AWS audit completed.'
                        },
                        toolResult: {
                            type: 'audit_summary',
                            runDir: '/tmp/audit-1',
                            summaryPath: '/tmp/audit-1/summary.json',
                            reportPath: '/tmp/audit-1/report.txt',
                            successCount: 10,
                            failureCount: 0,
                            skippedCount: 1
                        },
                        metadata: {provider: 'bedrock', modelId: 'us.amazon.nova-pro-v1:0'},
                        timestamp: '2026-04-10T10:01:00Z'
                    }
                ]
            })),
            http.get('/api/artifacts/files', () => HttpResponse.json({error: 'Run directory not found.'}, {status: 404}))
        );

        render(<Home/>);
        const user = userEvent.setup();

        const sessionTitle = await screen.findByText('run aws audit');
        await user.click(sessionTitle.closest('button'));
        await user.click(await screen.findByRole('button', {name: /show files/i}));

        expect(await screen.findByText('Files in run directory')).toBeInTheDocument();
        expect(screen.getByText(/This run directory is no longer available on disk\./i)).toBeInTheDocument();
        expect(screen.getByText(/Run directory not found\./i)).toBeInTheDocument();
    });

    it('keeps the generic artifact preview failure path for non-404 errors', async () => {
        server.use(
            ...defaultRuntimeHandlers({
                sessions: [
                    {
                        sessionId: 'session-1',
                        title: 'run aws audit',
                        summary: 'Audit complete.',
                        model: 'llama3:8b',
                        createdAt: '2026-04-10T10:00:00Z',
                        updatedAt: '2026-04-10T10:01:00Z',
                        messageCount: 2
                    }
                ]
            }),
            http.get('/api/sessions/session-1', () => HttpResponse.json({
                sessionId: 'session-1',
                title: 'run aws audit',
                summary: 'Audit complete.',
                model: 'llama3:8b',
                createdAt: '2026-04-10T10:00:00Z',
                updatedAt: '2026-04-10T10:01:00Z',
                pendingTool: null,
                messages: [
                    {role: 'user', content: 'run aws audit', tool: null, timestamp: '2026-04-10T10:00:00Z'},
                    {
                        role: 'assistant',
                        content: 'Audit complete.',
                        tool: {
                            used: true,
                            name: 'aws_region_audit',
                            status: 'success',
                            summary: 'AWS audit completed.'
                        },
                        toolResult: {
                            type: 'audit_summary',
                            runDir: '/tmp/audit-1',
                            summaryPath: '/tmp/audit-1/summary.json',
                            reportPath: '/tmp/audit-1/report.txt',
                            successCount: 10,
                            failureCount: 0,
                            skippedCount: 1
                        },
                        metadata: {provider: 'bedrock', modelId: 'us.amazon.nova-pro-v1:0'},
                        timestamp: '2026-04-10T10:01:00Z'
                    }
                ]
            })),
            http.get('/api/artifacts/preview', () => HttpResponse.json({error: 'Preview backend failed.'}, {status: 500}))
        );

        render(<Home/>);
        const user = userEvent.setup();

        const sessionTitle = await screen.findByText('run aws audit');
        await user.click(sessionTitle.closest('button'));
        await user.click(await screen.findByRole('button', {name: /open summary/i}));

        expect(await screen.findByText('Summary preview')).toBeInTheDocument();
        expect(screen.getAllByText(/Preview backend failed\./i).length).toBeGreaterThan(0);
        expect(screen.queryByText(/This artifact is no longer available on disk\./i)).not.toBeInTheDocument();
    });

    it('exports a session through the backend download endpoint', async () => {
        server.use(
            ...defaultRuntimeHandlers({
                sessions: [
                    {
                        sessionId: 'session-1',
                        title: 'run aws audit',
                        summary: 'Audit complete.',
                        model: 'llama3:8b',
                        createdAt: '2026-04-10T10:00:00Z',
                        updatedAt: '2026-04-10T10:01:00Z',
                        messageCount: 2
                    }
                ]
            }),
            http.get('/api/sessions/session-1/export', ({request}) => {
                expect(new URL(request.url).searchParams.get('format')).toBe('json');
                return new HttpResponse('{"sessionId":"session-1"}', {
                    status: 200,
                    headers: {
                        'Content-Type': 'application/json',
                        'Content-Disposition': 'attachment; filename="session-1.json"'
                    }
                });
            })
        );

        if (!window.URL.createObjectURL) {
            window.URL.createObjectURL = () => 'blob:test';
        }
        if (!window.URL.revokeObjectURL) {
            window.URL.revokeObjectURL = () => {
            };
        }
        const createObjectUrlSpy = vi.spyOn(window.URL, 'createObjectURL').mockReturnValue('blob:test');
        const revokeObjectUrlSpy = vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => {
        });
        const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
        });

        render(<Home/>);
        const user = userEvent.setup();

        expect(await screen.findByText('run aws audit')).toBeInTheDocument();
        await user.click(screen.getByRole('button', {name: /Export session run aws audit/i}));

        expect(createObjectUrlSpy).toHaveBeenCalled();
        expect(clickSpy).toHaveBeenCalled();
        expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:test');
    });

    it('shows a backend-unavailable error when session loading fails', async () => {
        server.use(
            http.get('/api/models', () => HttpResponse.json({
                provider: 'ollama',
                defaultProvider: 'ollama',
                providers: ['ollama'],
                defaultModel: 'llama3:8b',
                models: ['llama3:8b']
            })),
            http.get('/api/models/status', () => HttpResponse.json({
                provider: 'ollama',
                status: 'ready',
                message: 'Ollama is reachable and ready.'
            })),
            http.get('/api/sessions', () => HttpResponse.json({}, {status: 500}))
        );

        render(<Home/>);

        expect(await screen.findByText(/^Failed to load sessions\.$/i)).toBeInTheDocument();
    });
});
