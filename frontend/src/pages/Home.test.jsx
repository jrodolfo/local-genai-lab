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
  deleteSession: vi.fn()
}));

import { sendMessage, streamMessage } from '../api/chatApi';
import { deleteSession, getSession, listSessions } from '../api/sessionApi';

describe('Home', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    listSessions.mockResolvedValue([]);
    getSession.mockResolvedValue({
      sessionId: 'session-1',
      title: 'run aws audit',
      model: 'llama3:8b',
      createdAt: '2026-04-10T10:00:00Z',
      updatedAt: '2026-04-10T10:01:00Z',
      messages: [
        { role: 'user', content: 'run aws audit', tool: null, timestamp: '2026-04-10T10:00:00Z' },
        {
          role: 'assistant',
          content: 'Audit complete.',
          tool: { used: true, name: 'aws_region_audit', status: 'success', summary: 'AWS audit completed.' },
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
      tool: {
        used: true,
        name: 'aws_region_audit',
        status: 'success',
        summary: 'AWS audit completed.'
      }
    });

    render(<Home />);
    const user = userEvent.setup();

    await user.click(screen.getByLabelText(/Streaming/i));
    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'run aws audit');
    await user.click(screen.getByRole('button', { name: /send/i }));

    expect(await screen.findByText('Audit complete.')).toBeInTheDocument();
    expect(screen.getByText(/used tool: aws_region_audit/i)).toBeInTheDocument();
  });

  it('renders streamed provenance before tokens complete', async () => {
    streamMessage.mockImplementation(async ({ onMetadata, onToken }) => {
      onMetadata({
        sessionId: 'session-123',
        tool: {
          used: true,
          name: 'read_report_summary',
          status: 'success',
          summary: 'Read audit report.'
        }
      });
      onToken('Latest ');
      onToken('report ready.');
    });

    render(<Home />);
    const user = userEvent.setup();

    await user.type(screen.getByPlaceholderText(/Type your prompt/i), 'read the latest audit report');
    await user.click(screen.getByRole('button', { name: /send/i }));

    await waitFor(() => {
      expect(screen.getByText('Latest report ready.')).toBeInTheDocument();
    });

    expect(screen.getByText(/used tool: read_report_summary/i)).toBeInTheDocument();
  });

  it('loads and opens an existing session from the sidebar', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
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

    expect(await screen.findByText('Audit complete.')).toBeInTheDocument();
    expect(screen.getByText(/used tool: aws_region_audit/i)).toBeInTheDocument();
    expect(getSession).toHaveBeenCalledWith('session-1');
  });

  it('starts a new chat by clearing the current conversation', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
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
    expect(await screen.findByText('Audit complete.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /new chat/i }));

    expect(screen.queryByText('Audit complete.')).not.toBeInTheDocument();
    expect(screen.getByText(/Ask something to start a conversation./i)).toBeInTheDocument();
  });

  it('deletes a session from the sidebar', async () => {
    listSessions.mockResolvedValue([
      {
        sessionId: 'session-1',
        title: 'run aws audit',
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
});
