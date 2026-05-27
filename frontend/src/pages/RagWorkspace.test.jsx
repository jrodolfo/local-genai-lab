import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http, server } from '../test/mswServer';
import RagWorkspace from './RagWorkspace';

describe('RagWorkspace', () => {
  it('loads status, submits a docs query, and renders cited sources', async () => {
    server.use(
      http.get('/api/rag/status', () => HttpResponse.json({
        enabled: true,
        indexed: true,
        corpusRoot: '/repo/docs',
        documentCount: 12,
        chunkCount: 48,
        retrievalMode: 'lexical'
      })),
      http.get('/api/models', () => HttpResponse.json({
        provider: 'ollama',
        defaultProvider: 'ollama',
        providers: ['ollama', 'bedrock'],
        defaultModel: 'llama3:8b',
        models: ['llama3:8b']
      })),
      http.post('/api/rag/query', async ({ request }) => {
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
      })
    );

    render(<RagWorkspace />);
    const user = userEvent.setup();

    expect(await screen.findByText(/Status: ready/i)).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText(/Ask a question about the project docs/i), 'How does provider selection work?');
    await user.click(screen.getByRole('button', { name: /Ask docs corpus/i }));

    expect(await screen.findByText(/Provider selection is handled by the provider registry/i)).toBeInTheDocument();
    expect(screen.getByText('Sources')).toBeInTheDocument();
    expect(screen.getByText('architecture.md')).toBeInTheDocument();
  });

  it('shows a disabled state when the backend reports RAG is off', async () => {
    server.use(
      http.get('/api/rag/status', () => HttpResponse.json({
        enabled: false,
        indexed: false,
        corpusRoot: '/repo/docs',
        documentCount: 0,
        chunkCount: 0,
        retrievalMode: 'lexical'
      })),
      http.get('/api/models', () => HttpResponse.json({
        provider: 'ollama',
        defaultProvider: 'ollama',
        providers: ['ollama'],
        defaultModel: 'llama3:8b',
        models: ['llama3:8b']
      }))
    );

    render(<RagWorkspace />);

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
        retrievalMode: 'lexical'
      })),
      http.get('/api/models', () => HttpResponse.json({
        provider: 'ollama',
        defaultProvider: 'ollama',
        providers: ['ollama'],
        defaultModel: 'llama3:8b',
        models: ['llama3:8b']
      })),
      http.post('/api/rag/query', () => HttpResponse.json({ error: 'RAG backend failed.' }, { status: 503 }))
    );

    render(<RagWorkspace />);
    const user = userEvent.setup();

    await screen.findByText(/Status: ready/i);
    await user.type(screen.getByPlaceholderText(/Ask a question about the project docs/i), 'What is MCP?');
    await user.click(screen.getByRole('button', { name: /Ask docs corpus/i }));

    expect(await screen.findByText(/^RAG backend failed\.$/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByText('Sources')).not.toBeInTheDocument();
    });
  });
});
