import {sendMessage, streamMessage} from './chatApi';

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
                toolResult: {
                    type: 'audit_summary',
                    successCount: 10,
                    failureCount: 0
                },
                tool: {
                    used: true,
                    name: 'aws_region_audit'
                }
            })
        });

        const result = await sendMessage({message: 'run audit', provider: 'ollama', model: 'llama3:8b'});

        expect(result.response).toBe('Answer');
        expect(result.sessionId).toBe('session-123');
        expect(result.metadata.provider).toBe('bedrock');
        expect(result.metadata.totalTokens).toBe(46);
        expect(result.pendingTool.toolName).toBe('s3_cloudwatch_report');
        expect(result.tool.name).toBe('aws_region_audit');
        expect(result.toolResult.type).toBe('audit_summary');
    });

    it('streams typed json chat events', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            createStreamResponse([
                'event: chat\ndata: {"type":"start","sessionId":"session-123","tool":{"used":true,"name":"aws_region_audit","status":"success","summary":"done"},"toolResult":{"type":"audit_summary","successCount":10,"failureCount":0},"pendingTool":{"toolName":"s3_cloudwatch_report","reason":"s3 cloudwatch metrics request","missingFields":["bucket"]},"metadata":null}\n\n',
                'event: chat\ndata: {"type":"delta","text":" Hello"}\n\n',
                'event: chat\ndata: {"type":"delta","text":"world"}\n\n',
                'event: chat\ndata: {"type":"complete","sessionId":"session-123","tool":{"used":true,"name":"aws_region_audit","status":"success","summary":"done"},"pendingTool":null,"metadata":{"provider":"bedrock","modelId":"amazon.nova-lite-v1:0","totalTokens":46,"durationMs":412}}\n\n'
            ])
        );

        const events = [];

        await streamMessage({
            message: 'run audit',
            provider: 'ollama',
            model: 'llama3:8b',
            onEvent: (event) => events.push(event)
        });

        expect(events).toHaveLength(4);
        expect(events[0].type).toBe('start');
        expect(events[0].sessionId).toBe('session-123');
        expect(events[0].tool.name).toBe('aws_region_audit');
        expect(events[0].toolResult.type).toBe('audit_summary');
        expect(events[0].pendingTool.toolName).toBe('s3_cloudwatch_report');
        expect(events[1]).toEqual({type: 'delta', text: ' Hello'});
        expect(events[2]).toEqual({type: 'delta', text: 'world'});
        expect(events[3].type).toBe('complete');
        expect(events[3].metadata.provider).toBe('bedrock');
        expect(events[3].metadata.totalTokens).toBe(46);
    });

    it('ignores invalid event payloads safely', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(
            createStreamResponse([
                'event: chat\ndata: not-json\n\n',
                'event: chat\ndata: {"type":"delta","text":"Hello"}\n\n',
                'event: chat\ndata: {"type":"complete"}\n\n'
            ])
        );

        const events = [];

        await streamMessage({
            message: 'run audit',
            provider: 'ollama',
            model: 'llama3:8b',
            onEvent: (event) => events.push(event)
        });

        expect(events).toEqual([
            {type: 'delta', text: 'Hello'},
            {type: 'complete'}
        ]);
    });
});
