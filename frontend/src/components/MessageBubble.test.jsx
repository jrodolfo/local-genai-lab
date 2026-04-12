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

  it('renders provider metadata for assistant messages', () => {
    render(
      <MessageBubble
        role="assistant"
        content="Done."
        tool={null}
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
        tool={null}
        showTechnicalDetails={false}
        metadata={{
          provider: 'bedrock',
          modelId: 'amazon.nova-lite-v1:0'
        }}
      />
    );

    expect(screen.queryByText(/technical details/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/provider: bedrock/i)).not.toBeInTheDocument();
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
