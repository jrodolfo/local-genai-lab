import { sendMessage, streamMessage } from './chatApi';

function createStreamResponse(chunks) {
  return {
    ok: true,
    body: new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(new TextEncoder().encode(chunk)));
        controller.close();
      }
    })
  };
}

describe('chatApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns tool metadata from sendMessage', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({
        response: 'Answer',
        model: 'llama3:8b',
        sessionId: 'session-123',
        tool: {
          used: true,
          name: 'aws_region_audit'
        }
      })
    });

    const result = await sendMessage({ message: 'run audit', model: 'llama3:8b' });

    expect(result.response).toBe('Answer');
    expect(result.sessionId).toBe('session-123');
    expect(result.tool.name).toBe('aws_region_audit');
  });

  it('streams metadata before tokens', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      createStreamResponse([
        'event: metadata\ndata: {"sessionId":"session-123","tool":{"used":true,"name":"aws_region_audit","status":"success","summary":"done"}}\n\n',
        'data: Hello\n\n',
        'data:world\n\n',
        'data:[DONE]\n\n'
      ])
    );

    const tokens = [];
    const metadataEvents = [];

    await streamMessage({
      message: 'run audit',
      model: 'llama3:8b',
      onMetadata: (payload) => metadataEvents.push(payload),
      onToken: (token) => tokens.push(token)
    });

    expect(metadataEvents).toHaveLength(1);
    expect(metadataEvents[0].sessionId).toBe('session-123');
    expect(metadataEvents[0].tool.name).toBe('aws_region_audit');
    expect(tokens).toEqual([' Hello', 'world']);
  });

  it('ignores invalid metadata payloads safely', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      createStreamResponse([
        'event: metadata\ndata: not-json\n\n',
        'data:Hello\n\n',
        'data:[DONE]\n\n'
      ])
    );

    const metadataEvents = [];
    const tokens = [];

    await streamMessage({
      message: 'run audit',
      model: 'llama3:8b',
      onMetadata: (payload) => metadataEvents.push(payload),
      onToken: (token) => tokens.push(token)
    });

    expect(metadataEvents).toEqual([]);
    expect(tokens).toEqual(['Hello']);
  });
});
