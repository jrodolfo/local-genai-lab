import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Home from './Home';

vi.mock('../api/chatApi', () => ({
  sendMessage: vi.fn(),
  streamMessage: vi.fn()
}));

import { sendMessage, streamMessage } from '../api/chatApi';

describe('Home', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
});
