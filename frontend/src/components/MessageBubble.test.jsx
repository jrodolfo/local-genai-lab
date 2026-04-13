import { render, screen } from '@testing-library/react';
import MessageBubble from './MessageBubble';

describe('MessageBubble', () => {
  it('renders assistant provenance when tool metadata is present', () => {
    render(
      <MessageBubble
        role="assistant"
        content="Done."
        tool={{
          used: true,
          name: 'aws_region_audit',
          status: 'success',
          summary: 'AWS audit completed.'
        }}
      />
    );

    expect(screen.getByText('Done.')).toBeInTheDocument();
    expect(screen.getByText(/used tool: aws_region_audit/i)).toBeInTheDocument();
    expect(screen.getByText(/status: success/i)).toBeInTheDocument();
    expect(screen.getByText(/AWS audit completed./i)).toBeInTheDocument();
  });

  it('renders simple inline markdown in assistant messages', () => {
    render(
      <MessageBubble
        role="assistant"
        content={'1. **Preparation**: Use `Fibonacci` as the example.'}
      />
    );

    expect(screen.getByText('Preparation')).toContainHTML('<strong>Preparation</strong>');
    expect(screen.getByText('Fibonacci')).toContainHTML('<code>Fibonacci</code>');
  });

  it('renders structured report results for supported tool payloads', () => {
    render(
      <MessageBubble
        role="assistant"
        content="I found recent reports."
        tool={{ used: true, name: 'list_recent_reports', status: 'success', summary: 'Found 2 recent reports.' }}
        toolResult={{
          type: 'report_list',
          reportType: 'all',
          reports: [
            {
              report_type: 'audit',
              created_at: '2026-04-12T10:00:00Z',
              run_dir: '/tmp/audit-1',
              summary_json: '/tmp/audit-1/summary.json',
              report_txt: '/tmp/audit-1/report.txt'
            }
          ]
        }}
      />
    );

    expect(screen.getByText('reports')).toBeInTheDocument();
    expect(screen.getByText(/type: all/i)).toBeInTheDocument();
    expect(screen.getByText(/^audit$/i)).toBeInTheDocument();
    expect(screen.getByText(/\/tmp\/audit-1/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /view summary/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /view report/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /list files/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /copy run dir/i })).toBeInTheDocument();
  });

  it('renders provider metadata for assistant messages', () => {
    render(
      <MessageBubble
        role="assistant"
        content="Done."
        tool={{
          used: true,
          name: 'aws_region_audit',
          status: 'success',
          summary: 'AWS audit completed.'
        }}
        showTechnicalDetails
        metadata={{
          provider: 'bedrock',
          modelId: 'amazon.nova-lite-v1:0',
          stopReason: 'end_turn',
          inputTokens: 12,
          outputTokens: 34,
          totalTokens: 46,
          durationMs: 412,
          providerLatencyMs: 321
        }}
      />
    );

    expect(screen.getByText(/technical details/i)).toBeInTheDocument();
    expect(screen.getByText(/tool completed successfully; final wording still depends on the selected model/i)).toBeInTheDocument();
    expect(screen.getByText(/provider: bedrock/i)).toBeInTheDocument();
    expect(screen.getByText(/model: amazon.nova-lite-v1:0/i)).toBeInTheDocument();
    expect(screen.getByText(/stop reason: end_turn/i)).toBeInTheDocument();
    expect(screen.getByText(/tokens: 12 in \/ 34 out \/ 46 total/i)).toBeInTheDocument();
    expect(screen.getByText(/duration: 412 ms/i)).toBeInTheDocument();
    expect(screen.getByText(/provider latency: 321 ms/i)).toBeInTheDocument();
  });

  it('hides provider metadata when technical details are disabled', () => {
    render(
      <MessageBubble
        role="assistant"
        content="Done."
        tool={{
          used: true,
          name: 'aws_region_audit',
          status: 'success',
          summary: 'AWS audit completed.'
        }}
        showTechnicalDetails={false}
        metadata={{
          provider: 'bedrock',
          modelId: 'amazon.nova-lite-v1:0'
        }}
      />
    );

    expect(screen.queryByText(/technical details/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/provider: bedrock/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/tool completed successfully; final wording still depends on the selected model/i)).not.toBeInTheDocument();
  });

  it('does not render provenance for assistant messages without tool metadata', () => {
    render(<MessageBubble role="assistant" content="No tool used." tool={null} metadata={null} />);

    expect(screen.getByText('No tool used.')).toBeInTheDocument();
    expect(screen.queryByText(/used tool:/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/technical details/i)).not.toBeInTheDocument();
  });

  it('does not render provenance for user messages', () => {
    render(
      <MessageBubble
        role="user"
        content="Hello"
        tool={{
          used: true,
          name: 'aws_region_audit',
          status: 'success',
          summary: 'Should be hidden.'
        }}
        metadata={{
          provider: 'bedrock',
          modelId: 'amazon.nova-lite-v1:0'
        }}
      />
    );

    expect(screen.getByText('Hello')).toBeInTheDocument();
    expect(screen.queryByText(/used tool:/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/technical details/i)).not.toBeInTheDocument();
  });
});
