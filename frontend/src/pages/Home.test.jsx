import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Home from './Home';

vi.mock('../api/chatApi', () => ({
  sendMessage: vi.fn(),
  streamMessage: vi.fn()
}));

vi.mock('../api/sessionApi', () => ({
  listSessions: vi.fn(),
  getSession: vi.fn(),
  deleteSession: vi.fn(),
  exportSession: vi.fn()
}));

import { sendMessage, streamMessage } from '../api/chatApi';
import { deleteSession, exportSession, getSession, listSessions } from '../api/sessionApi';

describe('Home', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    exportSession.mockResolvedValue({
      blob: new Blob(['{"sessionId":"session-1"}'], { type: 'application/json' }),
      filename: 'session-1.json'
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
          timestamp: '2026-04-10T10:01:00Z'
        }
      ]
    });
    deleteSession.mockResolvedValue(undefined);
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
      metadata: {
        provider: 'bedrock',
        modelId: 'amazon.nova-lite-v1:0',
        totalTokens: 46,
        durationMs: 412
      }
    });

    render(<Home />);
    const user = userEvent.setup();

    await user.click(screen.getByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
    await user.click(screen.getByRole('button', { name: /send/i }));

    expect((await screen.findAllByText('Audit complete.')).length).toBeGreaterThan(0);
    expect(screen.getByText(/used tool: aws_region_audit/i)).toBeInTheDocument();
    expect(screen.queryByText(/provider: bedrock/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/tokens: \? in \/ \? out \/ 46 total/i)).not.toBeInTheDocument();
    expect(screen.getByText(/awaiting input for tool:/i)).toBeInTheDocument();
    expect(screen.getByText(/missing: bucket/i)).toBeInTheDocument();
  });

  it('renders streamed provenance before tokens complete', async () => {
    streamMessage.mockImplementation(async ({ onMetadata, onToken }) => {
      onMetadata({
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
      onToken('Latest ');
      onToken('report ready.');
      onMetadata({
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
    window.localStorage.setItem('llm-pet-project.debug-mode', 'true');
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
        durationMs: 412
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
  });

  it('toggles technical details on and off', async () => {
    render(<Home />);
    const user = userEvent.setup();
    const toggle = screen.getByRole('checkbox', { name: /show technical details/i });

    expect(toggle).not.toBeChecked();

    await user.click(toggle);
    expect(toggle).toBeChecked();
    expect(window.localStorage.getItem('llm-pet-project.debug-mode')).toBe('true');

    await user.click(toggle);
    expect(toggle).not.toBeChecked();
    expect(window.localStorage.getItem('llm-pet-project.debug-mode')).toBe('false');
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

    expect(exportSession).toHaveBeenCalledWith('session-1');
    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:test');
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
});
