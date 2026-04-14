import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Home from './Home';

vi.mock('../api/chatApi', () => ({
  sendMessage: vi.fn(),
  streamMessage: vi.fn()
}));

vi.mock('../api/modelApi', () => ({
  listAvailableModels: vi.fn()
}));

vi.mock('../api/artifactApi', () => ({
  listArtifacts: vi.fn(),
  previewArtifact: vi.fn()
}));

vi.mock('../api/sessionApi', () => ({
  listSessions: vi.fn(),
  getSession: vi.fn(),
  deleteSession: vi.fn(),
  exportSession: vi.fn(),
  importSession: vi.fn()
}));

import { sendMessage, streamMessage } from '../api/chatApi';
import { listAvailableModels } from '../api/modelApi';
import { listArtifacts, previewArtifact } from '../api/artifactApi';
import { deleteSession, exportSession, getSession, importSession, listSessions } from '../api/sessionApi';

describe('Home', () => {
  let scrollToSpy;

  afterEach(() => {
    vi.restoreAllMocks();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    scrollToSpy = vi.fn();
    Object.defineProperty(HTMLElement.prototype, 'scrollTo', {
      configurable: true,
      value: scrollToSpy
    });
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined)
      }
    });
    listAvailableModels.mockResolvedValue({
      provider: 'ollama',
      defaultModel: 'llama3:8b',
      models: ['llama3:8b', 'mistral:7b', 'codellama:70b']
    });
    exportSession.mockResolvedValue({
      blob: new Blob(['{"sessionId":"session-1"}'], { type: 'application/json' }),
      filename: 'session-1.json'
    });
    importSession.mockResolvedValue({
      sessionId: 'imported-session',
      title: 'imported title',
      summary: 'imported summary',
      idChanged: false,
      messageCount: 2
    });
    listSessions.mockResolvedValue([]);
    getSession.mockResolvedValue({
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
          metadata: {
            provider: 'bedrock',
            modelId: 'amazon.nova-lite-v1:0',
            stopReason: 'end_turn',
            inputTokens: 12,
            outputTokens: 34,
            totalTokens: 46,
            durationMs: 412,
            providerLatencyMs: 321,
            backendDurationMs: 450,
            uiWaitMs: 490
          },
          timestamp: '2026-04-10T10:01:00Z'
        }
      ]
    });
    deleteSession.mockResolvedValue(undefined);
    listArtifacts.mockResolvedValue([
      {
        name: 'report.txt',
        path: '/tmp/audit-1/report.txt',
        relativePath: 'audit/aws-audit-2026-04-10/report.txt',
        size: 128,
        previewable: true
      }
    ]);
    previewArtifact.mockResolvedValue({
      fileName: 'summary.json',
      path: '/tmp/audit-1/summary.json',
      relativePath: 'audit/aws-audit-2026-04-10/summary.json',
      contentType: 'application/json',
      size: 42,
      truncated: false,
      content: '{"success_count":10,"failure_count":0}'
    });
  });

  it('renders provenance for non-streaming chat responses', async () => {
    sendMessage.mockResolvedValue({
      response: 'Audit complete.',
      model: 'llama3:8b',
      sessionId: 'session-123',
      pendingTool: {
        toolName: 's3_cloudwatch_report',
        reason: 's3 cloudwatch metrics request',
        missingFields: ['bucket']
      },
      tool: {
        used: true,
        name: 'aws_region_audit',
        status: 'success',
        summary: 'AWS audit completed.'
      },
      toolResult: {
        type: 'audit_summary',
        accountId: '123456789012',
        runDir: '/tmp/audit-1',
        summaryPath: '/tmp/audit-1/summary.json',
        reportPath: '/tmp/audit-1/report.txt',
        successCount: 10,
        failureCount: 0,
        skippedCount: 1
      },
      metadata: {
        provider: 'bedrock',
        modelId: 'amazon.nova-lite-v1:0',
        totalTokens: 46,
        durationMs: 412
      }
    });

    render(<Home />);
    const user = userEvent.setup();

    expect(await screen.findByRole('combobox', { name: /model/i })).toHaveValue('llama3:8b');
    expect(screen.getByText(/provider: Ollama/i)).toBeInTheDocument();
    await user.click(screen.getByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
    await user.click(screen.getByRole('button', { name: /send/i }));

    expect((await screen.findAllByText('Audit complete.')).length).toBeGreaterThan(0);
    expect(screen.getByText(/used tool: aws_region_audit/i)).toBeInTheDocument();
    expect(screen.getByText('aws audit')).toBeInTheDocument();
    expect(screen.getByText(/success: 10/i)).toBeInTheDocument();
    expect(screen.queryByText(/provider: bedrock/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/tokens: \? in \/ \? out \/ 46 total/i)).not.toBeInTheDocument();
    expect(screen.getByText(/awaiting input for tool:/i)).toBeInTheDocument();
    expect(screen.getByText(/missing: bucket/i)).toBeInTheDocument();
  });

  it('shows the active provider in the header for bedrock mode', async () => {
    listAvailableModels.mockResolvedValue({
      provider: 'bedrock',
      defaultModel: 'us.amazon.nova-pro-v1:0',
      models: ['us.amazon.nova-pro-v1:0', 'us.amazon.nova-lite-v1:0']
    });

    render(<Home />);

    expect(await screen.findByRole('combobox', { name: /model/i })).toHaveValue('us.amazon.nova-pro-v1:0');
    expect(screen.getByText(/provider: Bedrock/i)).toBeInTheDocument();
  });

  it('shows a clear waiting message while a non-streaming request is in flight', async () => {
    let resolveRequest;
    sendMessage.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = resolve;
        })
    );

    render(<Home />);
    const user = userEvent.setup();

    await screen.findByRole('combobox', { name: /model/i });
    await user.click(screen.getByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
    await user.click(screen.getByRole('button', { name: /send/i }));

    expect(screen.getByRole('button', { name: /working/i })).toBeDisabled();
    expect(screen.getByText(/Waiting for response\.\.\./i)).toBeInTheDocument();

    resolveRequest({
      response: 'Audit complete.',
      model: 'llama3:8b',
      sessionId: 'session-123',
      pendingTool: null,
      tool: null,
      toolResult: null,
      metadata: null
    });

    await waitFor(() => {
      expect(screen.queryByText(/Waiting for response/i)).not.toBeInTheDocument();
    });
  });

  it('scrolls the chat window to the latest message', async () => {
    sendMessage.mockResolvedValue({
      response: 'Recursion is when a function calls itself.',
      model: 'llama3:8b',
      sessionId: 'session-123',
      pendingTool: null,
      tool: null,
      toolResult: null,
      metadata: null
    });

    render(<Home />);
    const user = userEvent.setup();

    await screen.findByRole('combobox', { name: /model/i });
    await user.click(screen.getByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'Explain recursion.');
    await user.click(screen.getByRole('button', { name: /send/i }));

    await screen.findByText(/Recursion is when a function calls itself\./i);

    expect(scrollToSpy).toHaveBeenCalled();
  });

  it('previews a structured report artifact from a tool result card', async () => {
    sendMessage.mockResolvedValue({
      response: 'Audit complete.',
      model: 'llama3:8b',
      sessionId: 'session-123',
      pendingTool: null,
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
      metadata: null
    });

    render(<Home />);
    const user = userEvent.setup();

    await screen.findByRole('combobox', { name: /model/i });
    await user.click(screen.getByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
    await user.click(screen.getByRole('button', { name: /send/i }));

    await user.click(await screen.findByRole('button', { name: /view summary/i }));

    expect(previewArtifact).toHaveBeenCalledWith('/tmp/audit-1/summary.json');
    expect(await screen.findByText(/audit\/aws-audit-2026-04-10\/summary\.json/i)).toBeInTheDocument();
    expect(screen.getByText(/\{"success_count":10,"failure_count":0\}/i)).toBeInTheDocument();
  });

  it('lists artifact files from a structured report card', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
        summary: 'Audit complete.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]);

    render(<Home />);
    const user = userEvent.setup();

    const sessionTitle = await screen.findByText('run aws audit');
    await user.click(sessionTitle.closest('button'));
    await user.click(await screen.findByRole('button', { name: /list files/i }));

    expect(listArtifacts).toHaveBeenCalledWith('/tmp/audit-1');
    expect(await screen.findByText(/audit\/aws-audit-2026-04-10\/report\.txt/i)).toBeInTheDocument();
  });

  it('shows a clear empty state when no local ollama models are installed', async () => {
    listAvailableModels.mockResolvedValue({
      provider: 'ollama',
      defaultModel: 'llama3:8b',
      models: []
    });

    render(<Home />);

    expect(await screen.findByText(/No Ollama models are installed locally/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send/i })).toBeDisabled();
  });

  it('renders streamed provenance before tokens complete', async () => {
    streamMessage.mockImplementation(async ({ onEvent }) => {
      onEvent({
        type: 'start',
        sessionId: 'session-123',
        pendingTool: {
          toolName: 'read_report_summary',
          reason: 'latest report lookup',
          missingFields: ['reportType']
        },
        tool: {
          used: true,
          name: 'read_report_summary',
          status: 'success',
          summary: 'Read audit report.'
        },
        metadata: null
      });
      onEvent({ type: 'delta', text: 'Latest ' });
      onEvent({ type: 'delta', text: 'report ready.' });
      onEvent({
        type: 'complete',
        sessionId: 'session-123',
        pendingTool: {
          toolName: 'read_report_summary',
          reason: 'latest report lookup',
          missingFields: ['reportType']
        },
        tool: {
          used: true,
          name: 'read_report_summary',
          status: 'success',
          summary: 'Read audit report.'
        },
        metadata: {
          provider: 'bedrock',
          modelId: 'amazon.nova-lite-v1:0',
          totalTokens: 46,
          durationMs: 412
        }
      });
    });

    render(<Home />);
    const user = userEvent.setup();

    await screen.findByRole('combobox', { name: /model/i });
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'read the latest audit report');
    await user.click(screen.getByRole('button', { name: /send/i }));

    await waitFor(() => {
      expect(screen.getByText('Latest report ready.')).toBeInTheDocument();
    });

    expect(screen.getByText(/used tool: read_report_summary/i)).toBeInTheDocument();
    expect(screen.queryByText(/provider: bedrock/i)).not.toBeInTheDocument();
    expect(screen.getByText(/awaiting input for tool:/i)).toBeInTheDocument();
    expect(screen.getByText(/missing: reportType/i)).toBeInTheDocument();
  });

  it('loads and opens an existing session from the sidebar', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
        summary: 'Audit complete.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]);

    render(<Home />);
    const user = userEvent.setup();

    const sessionTitle = await screen.findByText('run aws audit');
    expect(sessionTitle).toBeInTheDocument();
    await user.click(sessionTitle.closest('button'));

    expect((await screen.findAllByText('Audit complete.')).length).toBeGreaterThan(0);
    expect(screen.getByText(/used tool: aws_region_audit/i)).toBeInTheDocument();
    expect(screen.queryByText(/provider: bedrock/i)).not.toBeInTheDocument();
    expect(getSession).toHaveBeenCalledWith('session-1');
  });

  it('shows provider metadata when technical details are enabled', async () => {
    window.localStorage.setItem('local-genai-lab.debug-mode', 'true');
    sendMessage.mockResolvedValue({
      response: 'Audit complete.',
      model: 'llama3:8b',
      sessionId: 'session-123',
      pendingTool: null,
      tool: {
        used: true,
        name: 'aws_region_audit',
        status: 'success',
        summary: 'AWS audit completed.'
      },
      metadata: {
        provider: 'bedrock',
        modelId: 'amazon.nova-lite-v1:0',
        totalTokens: 46,
        durationMs: 412,
        backendDurationMs: 430
      }
    });

    render(<Home />);
    const user = userEvent.setup();

    expect(screen.getByRole('checkbox', { name: /show technical details/i })).toBeChecked();

    await user.click(screen.getByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
    await user.click(screen.getByRole('button', { name: /send/i }));

    expect((await screen.findAllByText('Audit complete.')).length).toBeGreaterThan(0);
    expect(screen.getByText(/provider: bedrock/i)).toBeInTheDocument();
    expect(screen.getByText(/tokens: \? in \/ \? out \/ 46 total/i)).toBeInTheDocument();
    expect(screen.getByText(/provider duration: 412 ms/i)).toBeInTheDocument();
    expect(screen.getByText(/backend total: 430 ms/i)).toBeInTheDocument();
    expect(screen.getByText(/ui wait: \d+ ms/i)).toBeInTheDocument();
  });

  it('toggles technical details on and off', async () => {
    render(<Home />);
    const user = userEvent.setup();
    const toggle = screen.getByRole('checkbox', { name: /show technical details/i });

    expect(toggle).not.toBeChecked();

    await user.click(toggle);
    expect(toggle).toBeChecked();
    expect(window.localStorage.getItem('local-genai-lab.debug-mode')).toBe('true');

    await user.click(toggle);
    expect(toggle).not.toBeChecked();
    expect(window.localStorage.getItem('local-genai-lab.debug-mode')).toBe('false');
  });

  it('shows pending tool state when a loaded session includes it', async () => {
    getSession.mockResolvedValue({
      sessionId: 'session-1',
      title: 'check bucket metrics',
      summary: 'Waiting for bucket name.',
      model: 'llama3:8b',
      createdAt: '2026-04-10T10:00:00Z',
      updatedAt: '2026-04-10T10:01:00Z',
      pendingTool: {
        toolName: 's3_cloudwatch_report',
        reason: 's3 cloudwatch metrics request',
        missingFields: ['bucket']
      },
      messages: [
        { role: 'user', content: 'check bucket metrics', tool: null, timestamp: '2026-04-10T10:00:00Z' },
        {
          role: 'assistant',
          content: 'I can run the S3 CloudWatch report, but I need the bucket name.',
          tool: { used: true, name: 's3_cloudwatch_report', status: 'clarification-needed', summary: 'Need bucket.' },
          timestamp: '2026-04-10T10:01:00Z'
        }
      ]
    });
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'check bucket metrics',
        summary: 'Waiting for bucket name.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]);

    render(<Home />);
    const user = userEvent.setup();

    const sessionTitle = await screen.findByText('check bucket metrics');
    await user.click(sessionTitle.closest('button'));

    expect(await screen.findByText(/awaiting input for tool:/i)).toBeInTheDocument();
    expect(screen.getByText(/missing: bucket/i)).toBeInTheDocument();
  });

  it('starts a new chat by clearing the current conversation', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
        summary: 'Audit complete.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]);

    render(<Home />);
    const user = userEvent.setup();

    const sessionTitle = await screen.findByText('run aws audit');
    await user.click(sessionTitle.closest('button'));
    expect((await screen.findAllByText('Audit complete.')).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /new chat/i }));

    expect(screen.queryByText(/awaiting input for tool:/i)).not.toBeInTheDocument();
    expect(screen.getByText(/Ask something to start a conversation./i)).toBeInTheDocument();
  });

  it('deletes a session from the sidebar', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
        summary: 'Audit complete.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]);

    render(<Home />);
    const user = userEvent.setup();

    expect(await screen.findByText('run aws audit')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Delete session run aws audit/i }));

    await waitFor(() => {
      expect(screen.queryByText('run aws audit')).not.toBeInTheDocument();
    });
    expect(deleteSession).toHaveBeenCalledWith('session-1');
  });

  it('exports a session from the sidebar', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
        summary: 'Audit complete.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]);
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

    expect(exportSession).toHaveBeenCalledWith('session-1', 'json');
    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:test');
  });

  it('exports a session as markdown from the sidebar', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
        summary: 'Audit complete.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]);
    exportSession.mockResolvedValue({
      blob: new Blob(['# run aws audit'], { type: 'text/markdown' }),
      filename: 'session-1.md'
    });
    if (!window.URL.createObjectURL) {
      window.URL.createObjectURL = () => 'blob:test-markdown';
    }
    if (!window.URL.revokeObjectURL) {
      window.URL.revokeObjectURL = () => {};
    }
    const createObjectUrlSpy = vi.spyOn(window.URL, 'createObjectURL').mockReturnValue('blob:test-markdown');
    const revokeObjectUrlSpy = vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => {});
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    render(<Home />);
    const user = userEvent.setup();

    expect(await screen.findByText('run aws audit')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Export markdown session run aws audit/i }));

    expect(exportSession).toHaveBeenCalledWith('session-1', 'markdown');
    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:test-markdown');
  });

  it('renders session summaries in the sidebar', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
        summary: 'Audit completed successfully for us-east-2 sts.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]);

    render(<Home />);

    expect(await screen.findByText('run aws audit')).toBeInTheDocument();
    expect(screen.getByText(/Audit completed successfully for us-east-2 sts./i)).toBeInTheDocument();
  });

  it('searches sessions through the backend query parameter', async () => {
    listSessions
      .mockResolvedValueOnce([
        {
          sessionId: 'session-1',
          title: 'run aws audit',
          summary: 'Audit complete.',
          model: 'llama3:8b',
          createdAt: '2026-04-10T10:00:00Z',
          updatedAt: '2026-04-10T10:01:00Z',
          messageCount: 2
        }
      ])
      .mockResolvedValueOnce([
        {
          sessionId: 'session-2',
          title: 'bedrock latency notes',
          summary: 'Nova latency comparison.',
          model: 'llama3:8b',
          createdAt: '2026-04-11T10:00:00Z',
          updatedAt: '2026-04-11T10:01:00Z',
          messageCount: 2
        }
      ]);

    render(<Home />);
    const user = userEvent.setup();

    expect(await screen.findByText('run aws audit')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Search sessions/i), 'bedrock');

    await waitFor(() => {
      expect(listSessions).toHaveBeenLastCalledWith({
        query: 'bedrock',
        provider: '',
        toolUsage: '',
        pending: false
      });
    });
    expect(await screen.findByText('bedrock latency notes')).toBeInTheDocument();
    expect(screen.queryByText('run aws audit')).not.toBeInTheDocument();
  });

  it('filters sessions by provider, tool usage, and pending state', async () => {
    listSessions
      .mockResolvedValueOnce([
        {
          sessionId: 'session-1',
          title: 'run aws audit',
          summary: 'Audit complete.',
          model: 'llama3:8b',
          createdAt: '2026-04-10T10:00:00Z',
          updatedAt: '2026-04-10T10:01:00Z',
          messageCount: 2
        }
      ])
      .mockResolvedValue([]);

    render(<Home />);
    const user = userEvent.setup();

    expect(await screen.findByText('run aws audit')).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(/Provider filter/i), 'bedrock');
    await waitFor(() => {
      expect(listSessions).toHaveBeenLastCalledWith({
        query: '',
        provider: 'bedrock',
        toolUsage: '',
        pending: false
      });
    });

    await user.selectOptions(screen.getByLabelText(/Tool usage filter/i), 'used');
    await waitFor(() => {
      expect(listSessions).toHaveBeenLastCalledWith({
        query: '',
        provider: 'bedrock',
        toolUsage: 'used',
        pending: false
      });
    });

    await user.click(screen.getByRole('checkbox', { name: /Pending only/i }));
    await waitFor(() => {
      expect(listSessions).toHaveBeenLastCalledWith({
        query: '',
        provider: 'bedrock',
        toolUsage: 'used',
        pending: true
      });
    });
  });

  it('shows a no-match message when search returns no sessions', async () => {
    listSessions.mockResolvedValueOnce([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
        summary: 'Audit complete.',
        model: 'llama3:8b',
        createdAt: '2026-04-10T10:00:00Z',
        updatedAt: '2026-04-10T10:01:00Z',
        messageCount: 2
      }
    ]).mockResolvedValueOnce([]);

    render(<Home />);
    const user = userEvent.setup();

    expect(await screen.findByText('run aws audit')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Search sessions/i), 'missing');

    expect(await screen.findByText(/No matching sessions./i)).toBeInTheDocument();
  });

  it('imports a json session and opens it', async () => {
    getSession.mockResolvedValueOnce({
      sessionId: 'imported-session',
      title: 'imported title',
      summary: 'imported summary',
      model: 'llama3:8b',
      createdAt: '2026-04-10T10:00:00Z',
      updatedAt: '2026-04-10T10:01:00Z',
      pendingTool: null,
      messages: [
        { role: 'user', content: 'imported question', tool: null, metadata: null, timestamp: '2026-04-10T10:00:00Z' },
        { role: 'assistant', content: 'imported answer', tool: null, metadata: null, timestamp: '2026-04-10T10:01:00Z' }
      ]
    });

    render(<Home />);
    const user = userEvent.setup();
    const fileInput = screen.getByLabelText(/Import session file/i);
    const file = new File(
      ['{"sessionId":"imported-session","messages":[{"role":"user","content":"hello"}]}'],
      'session.json',
      { type: 'application/json' }
    );

    await user.upload(fileInput, file);

    expect(importSession).toHaveBeenCalledWith(file);
    expect(await screen.findByText('imported answer')).toBeInTheDocument();
  });

  it('shows an error when json import fails', async () => {
    importSession.mockRejectedValueOnce(new Error('Import file is not valid JSON.'));

    render(<Home />);
    const user = userEvent.setup();
    const fileInput = screen.getByLabelText(/Import session file/i);
    const file = new File(['{'], 'invalid.json', { type: 'application/json' });

    await user.upload(fileInput, file);

    expect(await screen.findByText(/Import file is not valid JSON./i)).toBeInTheDocument();
  });
});
