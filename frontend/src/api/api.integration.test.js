import { listArtifacts, previewArtifact } from './artifactApi';
import { sendMessage, streamMessage } from './chatApi';
import { listAvailableModels, getProviderStatus } from './modelApi';
import { exportSession, importSession, listSessions } from './sessionApi';
import { HttpResponse, http, server, sseResponse } from '../test/mswServer';

describe('frontend api integration', () => {
  it('sends a chat request and returns the backend payload', async () => {
    server.use(
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
          tool: { used: true, name: 'aws_region_audit', status: 'success', summary: 'AWS audit completed.' },
          toolResult: { type: 'audit_summary', successCount: 10, failureCount: 0 }
        });
      })
    );

    const result = await sendMessage({ message: 'run aws audit', provider: 'ollama', model: 'llama3:8b' });

    expect(result.response).toBe('Audit complete.');
    expect(result.sessionId).toBe('session-123');
    expect(result.tool.name).toBe('aws_region_audit');
  });

  it('surfaces backend chat errors', async () => {
    server.use(
      http.post('/api/chat', () => HttpResponse.json({ error: 'Backend unavailable.' }, { status: 503 }))
    );

    await expect(sendMessage({ message: 'hello', provider: 'ollama', model: 'llama3:8b' }))
      .rejects.toThrow('Backend unavailable.');
  });

  it('streams typed SSE chat events through the chat api', async () => {
    server.use(
      http.post('/api/chat/stream', () => sseResponse([
        { type: 'start', sessionId: 'session-123' },
        { type: 'tool-execution-started', toolName: 'aws_region_audit' },
        { type: 'delta', text: 'Hello' },
        { type: 'complete', sessionId: 'session-123', metadata: { provider: 'bedrock', totalTokens: 42 } }
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
      { type: 'start', sessionId: 'session-123' },
      { type: 'tool-execution-started', toolName: 'aws_region_audit' },
      { type: 'delta', text: 'Hello' },
      { type: 'complete', sessionId: 'session-123', metadata: { provider: 'bedrock', totalTokens: 42 } }
    ]);
  });

  it('loads provider models and status through backend-shaped endpoints', async () => {
    server.use(
      http.get('/api/models', ({ request }) => {
        expect(new URL(request.url).searchParams.get('provider')).toBe('ollama');
        return HttpResponse.json({
          provider: 'ollama',
          defaultProvider: 'ollama',
          providers: ['ollama', 'bedrock'],
          defaultModel: 'llama3:8b',
          models: ['llama3:8b', 'mistral:7b']
        });
      }),
      http.get('/api/models/status', ({ request }) => {
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
      http.get('/api/sessions/session-1/export', () => new HttpResponse('{"sessionId":"session-1"}', {
        status: 200,
        headers: {
          'Content-Type': 'application/json',
          'Content-Disposition': 'attachment; filename="session-1.json"'
        }
      })),
      http.post('/api/sessions/import', async ({ request }) => {
        const formData = await request.formData();
        const file = formData.get('file');
        expect(file).toBeTruthy();
        expect(file?.name).toBe('blob');
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
    await expect(exported.blob.text()).resolves.toBe('{"sessionId":"session-1"}');

    const imported = await importSession(new File(['{}'], 'session.json', { type: 'application/json' }));
    expect(imported.sessionId).toBe('imported-session');
  });

  it('surfaces artifact and session loading failures', async () => {
    server.use(
      http.get('/api/sessions', () => HttpResponse.json({ error: 'Failed to load sessions.' }, { status: 500 })),
      http.get('/api/artifacts/files', () => HttpResponse.json({ error: 'Failed to list artifact files.' }, { status: 500 })),
      http.get('/api/artifacts/preview', () => HttpResponse.json({ error: 'Failed to preview artifact.' }, { status: 404 }))
    );

    await expect(listSessions()).rejects.toThrow('Failed to load sessions.');
    await expect(listArtifacts('/tmp/audit-1')).rejects.toThrow('Failed to list artifact files.');
    await expect(previewArtifact('/tmp/audit-1/summary.json')).rejects.toThrow('Failed to preview artifact.');
  });
});
