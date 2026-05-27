import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Home from './Home';
import { HttpResponse, http, server } from '../test/mswServer';

function defaultRuntimeHandlers(overrides = {}) {
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
      http.post('/api/chat', async ({ request }) => {
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

    render(<Home />);
    const user = userEvent.setup();

    expect(await screen.findByRole('combobox', { name: /model/i })).toHaveValue('llama3:8b');
    await user.click(screen.getByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
    await user.click(screen.getByRole('button', { name: /send/i }));

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

    render(<Home />);

    expect(await screen.findByRole('combobox', { name: /chat provider/i })).toHaveValue('ollama');
    expect(screen.getByText(/provider: Ollama/i)).toBeInTheDocument();
    expect(screen.getByText(/Ollama status: ready/i)).toBeInTheDocument();
    expect(screen.getByText(/Last checked:/i)).toBeInTheDocument();
  });

  it('shows a backend error when a non-streaming chat request fails', async () => {
    server.use(
      ...defaultRuntimeHandlers(),
      http.post('/api/chat', () => HttpResponse.json({ error: 'Provider request failed.' }, { status: 503 }))
    );

    render(<Home />);
    const user = userEvent.setup();

    await user.click(await screen.findByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
    await user.click(screen.getByRole('button', { name: /send/i }));

    expect(await screen.findByText(/^Provider request failed\.$/i)).toBeInTheDocument();
    expect(screen.queryByText('AWS audit result')).not.toBeInTheDocument();
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
          { role: 'user', content: 'run aws audit', tool: null, timestamp: '2026-04-10T10:00:00Z' },
          {
            role: 'assistant',
            content: 'Audit complete.',
            tool: { used: true, name: 'aws_region_audit', status: 'success', summary: 'AWS audit completed.' },
            toolResult: {
              type: 'audit_summary',
              runDir: '/tmp/audit-1',
              summaryPath: '/tmp/audit-1/summary.json',
              reportPath: '/tmp/audit-1/report.txt',
              successCount: 10,
              failureCount: 0,
              skippedCount: 1
            },
            metadata: { provider: 'bedrock', modelId: 'us.amazon.nova-pro-v1:0' },
            timestamp: '2026-04-10T10:01:00Z'
          }
        ]
      })),
      http.get('/api/artifacts/preview', ({ request }) => {
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

    render(<Home />);
    const user = userEvent.setup();

    const sessionTitle = await screen.findByText('run aws audit');
    await user.click(sessionTitle.closest('button'));
    await user.click(await screen.findByRole('button', { name: /open summary/i }));

    expect(await screen.findByText('Summary preview')).toBeInTheDocument();
    expect(screen.getByText(/Relative path: audit\/aws-audit-2026-04-10\/summary\.json/i)).toBeInTheDocument();
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
      http.get('/api/sessions/session-1/export', ({ request }) => {
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
      window.URL.revokeObjectURL = () => {};
    }
    const createObjectUrlSpy = vi.spyOn(window.URL, 'createObjectURL').mockReturnValue('blob:test');
    const revokeObjectUrlSpy = vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => {});
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    render(<Home />);
    const user = userEvent.setup();

    expect(await screen.findByText('run aws audit')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Export session run aws audit/i }));

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
      http.get('/api/sessions', () => HttpResponse.json({}, { status: 500 }))
    );

    render(<Home />);

    expect(await screen.findByText(/^Failed to load sessions\.$/i)).toBeInTheDocument();
  });
});
