/**
 * @fileoverview Tests for the main App component.
 * Verifies navigation between Chat and RAG modes based on backend RAG status.
 */
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import {http, HttpResponse, server} from './test/mswServer';

describe('App mode navigation', () => {
    beforeEach(() => {
        Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
            configurable: true,
            value: vi.fn()
        });
    });

    it('renders rag as a visible but disabled mode when rag is not enabled', async () => {
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
            http.get('/api/models/status', () => HttpResponse.json({
                provider: 'ollama',
                status: 'ready',
                message: 'Ollama is reachable and ready.'
            })),
            http.get('/api/sessions', () => HttpResponse.json([]))
        );

        render(<App/>);

        const chatTab = await screen.findByRole('tab', {name: /chat/i});
        const ragTab = await screen.findByRole('tab', {name: /^rag$/i});
        expect(chatTab).toBeDisabled();
        expect(chatTab).toHaveAttribute('aria-selected', 'true');
        expect(ragTab).toBeDisabled();
        expect(ragTab).toHaveAttribute('aria-disabled', 'true');
        expect(screen.getByText(/Enable `RAG_ENABLED=true` in the backend to use RAG mode\./i)).toBeInTheDocument();
    });

    it('lets the user switch to rag when rag is enabled', async () => {
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
            http.get('/api/models', ({request}) => {
                const provider = new URL(request.url).searchParams.get('provider');
                return HttpResponse.json({
                    provider: provider || 'ollama',
                    defaultProvider: 'ollama',
                    providers: ['ollama'],
                    defaultModel: 'llama3:8b',
                    models: ['llama3:8b']
                });
            }),
            http.get('/api/models/status', () => HttpResponse.json({
                provider: 'ollama',
                status: 'ready',
                message: 'Ollama is reachable and ready.'
            })),
            http.get('/api/sessions', () => HttpResponse.json([]))
        );

        render(<App/>);
        const user = userEvent.setup();

        const chatTab = await screen.findByRole('tab', {name: /chat/i});
        const ragTab = await screen.findByRole('tab', {name: /^rag$/i});

        expect(chatTab).toBeDisabled();
        expect(ragTab).not.toBeDisabled();

        await user.click(ragTab);

        expect(await screen.findByRole('heading', {name: /^rag$/i})).toBeInTheDocument();
        expect(ragTab).toBeDisabled();
        expect(ragTab).toHaveAttribute('aria-selected', 'true');
    });
});
