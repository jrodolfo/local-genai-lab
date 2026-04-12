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
        metadata: {
          provider: 'bedrock',
          modelId: 'amazon.nova-lite-v1:0',
          stopReason: 'end_turn',
          inputTokens: 12,
          outputTokens: 34,
          totalTokens: 46,
          durationMs: 412,
          providerLatencyMs: 321
        },
        pendingTool: {
          toolName: 's3_cloudwatch_report',
          reason: 's3 cloudwatch metrics request',
          missingFields: ['bucket']
        },
        tool: {
          used: true,
          name: 'aws_region_audit'
        }
      })
    });

    const result = await sendMessage({ message: 'run audit', model: 'llama3:8b' });

    expect(result.response).toBe('Answer');
    expect(result.sessionId).toBe('session-123');
    expect(result.metadata.provider).toBe('bedrock');
    expect(result.metadata.totalTokens).toBe(46);
    expect(result.pendingTool.toolName).toBe('s3_cloudwatch_report');
    expect(result.tool.name).toBe('aws_region_audit');
  });

  it('streams metadata before tokens', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      createStreamResponse([
        'event: metadata\ndata: {"sessionId":"session-123","tool":{"used":true,"name":"aws_region_audit","status":"success","summary":"done"},"pendingTool":{"toolName":"s3_cloudwatch_report","reason":"s3 cloudwatch metrics request","missingFields":["bucket"]},"metadata":null}\n\n',
        'data: Hello\n\n',
        'data:world\n\n',
        'event: metadata\ndata: {"sessionId":"session-123","tool":{"used":true,"name":"aws_region_audit","status":"success","summary":"done"},"pendingTool":null,"metadata":{"provider":"bedrock","modelId":"amazon.nova-lite-v1:0","totalTokens":46,"durationMs":412}}\n\n',
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

    expect(metadataEvents).toHaveLength(2);
    expect(metadataEvents[0].sessionId).toBe('session-123');
    expect(metadataEvents[0].tool.name).toBe('aws_region_audit');
    expect(metadataEvents[0].pendingTool.toolName).toBe('s3_cloudwatch_report');
    expect(metadataEvents[1].metadata.provider).toBe('bedrock');
    expect(metadataEvents[1].metadata.totalTokens).toBe(46);
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
