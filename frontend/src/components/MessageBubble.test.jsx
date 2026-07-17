/**
 * @fileoverview Tests for the MessageBubble component.
 * Verifies rendering of assistant and user messages, tool usage information,
 * markdown content, and structured tool results.
 */
import {render, screen} from '@testing-library/react';
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
        expect(screen.getByText(/tool used/i)).toBeInTheDocument();
        expect(screen.getByText(/^aws_region_audit$/i)).toBeInTheDocument();
        expect(screen.getByText(/^success$/i)).toBeInTheDocument();
        expect(screen.getByText(/AWS audit completed./i)).toBeInTheDocument();
    });

    it('renders simple inline markdown in assistant messages', () => {
        render(
            <MessageBubble
                role="assistant"
                content={'Use **Fibonacci** with `int` values.'}
            />
        );

        expect(screen.getByText('Fibonacci')).toContainHTML('<strong>Fibonacci</strong>');
        expect(screen.getByText('int')).toContainHTML('<code>int</code>');
    });

    it('renders block markdown for headings, lists, rules, and fenced code blocks', () => {
        render(
            <MessageBubble
                role="assistant"
                content={`### Recursion

1. Base case
2. Recursive case

---

- Trees
- Graphs

\`\`\`python
def factorial(n):
    return 1 if n == 0 else n * factorial(n - 1)
\`\`\``}
            />
        );

        expect(screen.getByRole('heading', {level: 3, name: 'Recursion'})).toBeInTheDocument();
        expect(screen.getAllByRole('list')).toHaveLength(2);
        expect(screen.getByText('Base case')).toBeInTheDocument();
        expect(screen.getByText('Recursive case')).toBeInTheDocument();
        expect(screen.getByText('Trees')).toBeInTheDocument();
        expect(screen.getByText('Graphs')).toBeInTheDocument();
        expect(screen.getByText(/def factorial\(n\):/i)).toBeInTheDocument();
        expect(screen.getByText('python')).toBeInTheDocument();
        expect(document.querySelector('.message-rule')).not.toBeNull();
    });

    it('renders structured report results for supported tool payloads', () => {
        render(
            <MessageBubble
                role="assistant"
                content="I found recent reports."
                tool={{used: true, name: 'list_recent_reports', status: 'success', summary: 'Found 2 recent reports.'}}
                showTechnicalDetails
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

        expect(screen.getByText('Recent reports')).toBeInTheDocument();
        expect(screen.getByText(/structured result available below/i)).toBeInTheDocument();
        expect(screen.getByText(/Report type: all/i)).toBeInTheDocument();
        expect(screen.getByText(/^audit$/i)).toBeInTheDocument();
        expect(screen.getByText(/\/tmp\/audit-1/i)).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /open summary/i})).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /open report/i})).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /show files/i})).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /copy run directory/i})).toBeInTheDocument();
    });

    it('hides structured tool results when technical details are disabled', () => {
        render(
            <MessageBubble
                role="assistant"
                content="Audit complete."
                tool={{used: true, name: 'aws_region_audit', status: 'success', summary: 'AWS audit completed.'}}
                showTechnicalDetails={false}
                toolResult={{
                    type: 'audit_summary',
                    reportType: 'audit',
                    accountId: '408887463418',
                    runDir: 'audit/aws-audit-2026-07-14_17-52-30',
                    successCount: 37,
                    failureCount: 0,
                    skippedCount: 0
                }}
            />
        );

        expect(screen.getByText(/tool used/i)).toBeInTheDocument();
        expect(screen.getByText(/^aws_region_audit$/i)).toBeInTheDocument();
        expect(screen.getByText(/^success$/i)).toBeInTheDocument();
        expect(screen.queryByText('AWS audit result')).not.toBeInTheDocument();
        expect(screen.queryByText(/Account: 408887463418/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/structured result available below/i)).not.toBeInTheDocument();
    });

    it('does not render missing audit counts as zero', () => {
        render(
            <MessageBubble
                role="assistant"
                content="The audit result is incomplete."
                tool={{used: true, name: 'aws_region_audit', status: 'success', summary: 'AWS audit completed with success_count=unknown.'}}
                showTechnicalDetails
                toolResult={{
                    type: 'audit_summary',
                    reportType: 'audit'
                }}
            />
        );

        expect(screen.getByText('AWS audit result')).toBeInTheDocument();
        expect(screen.queryByText(/^Success: 0$/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/^Failures: 0$/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/^Skipped: 0$/i)).not.toBeInTheDocument();
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
                    providerLatencyMs: 321,
                    backendDurationMs: 450,
                    uiWaitMs: 490,
                    phaseTimingsMs: {
                        toolExecutionMs: 120,
                        promptBuildMs: 35,
                        timeToFirstTokenMs: 280
                    }
                }}
            />
        );

        expect(screen.getByText(/Bedrock · amazon\.nova-lite-v1:0/i)).toBeInTheDocument();
        expect(screen.getByText(/technical details/i)).toBeInTheDocument();
        expect(screen.queryByText(/tool completed successfully; final wording still depends on the selected model/i)).not.toBeInTheDocument();
        expect(screen.getByText(/provider: bedrock/i)).toBeInTheDocument();
        expect(screen.getByText(/model: amazon.nova-lite-v1:0/i)).toBeInTheDocument();
        expect(screen.getByText(/stop reason: end_turn/i)).toBeInTheDocument();
        expect(screen.getByText(/tokens: 12 in \/ 34 out \/ 46 total/i)).toBeInTheDocument();
        expect(screen.getByText(/provider duration: 412 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/provider latency: 321 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/backend total: 450 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/ui wait: 490 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/tool execution: 120 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/prompt build: 35 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/time to first token: 280 ms/i)).toBeInTheDocument();
    });

    it('formats longer timing values into minutes, seconds, and milliseconds', () => {
        render(
            <MessageBubble
                role="assistant"
                content="Done."
                showTechnicalDetails
                metadata={{
                    provider: 'ollama',
                    modelId: 'llama3:8b',
                    backendDurationMs: 84474,
                    uiWaitMs: 84661
                }}
            />
        );

        expect(screen.getByText(/backend total: 1 m 24 s 474 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/ui wait: 1 m 24 s 661 ms/i)).toBeInTheDocument();
    });

    it('shows provider and model by default on assistant messages', () => {
        render(
            <MessageBubble
                role="assistant"
                content="Done."
                showTechnicalDetails={false}
                metadata={{
                    provider: 'ollama',
                    modelId: 'llama3:8b'
                }}
            />
        );

        expect(screen.getByText('Ollama · llama3:8b')).toBeInTheDocument();
        expect(screen.queryByText(/technical details/i)).not.toBeInTheDocument();
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

        expect(screen.getByText(/Bedrock · amazon\.nova-lite-v1:0/i)).toBeInTheDocument();
        expect(screen.queryByText(/technical details/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/provider: bedrock/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/tool completed successfully; final wording still depends on the selected model/i)).not.toBeInTheDocument();
    });

    it('does not render provenance for assistant messages without tool metadata', () => {
        render(<MessageBubble role="assistant" content="No tool used." tool={null} metadata={null}/>);

        expect(screen.getByText('No tool used.')).toBeInTheDocument();
        expect(screen.queryByText(/^tool used$/i)).not.toBeInTheDocument();
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
        expect(screen.queryByText(/^tool used$/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/technical details/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/Bedrock · amazon\.nova-lite-v1:0/i)).not.toBeInTheDocument();
    });

    it('renders failed tool status clearly', () => {
        render(
            <MessageBubble
                role="assistant"
                content="The tool failed."
                tool={{
                    used: true,
                    name: 'aws_region_audit',
                    status: 'failed',
                    summary: 'AWS credentials were invalid.'
                }}
            />
        );

        expect(screen.getByText(/^failed$/i)).toBeInTheDocument();
        expect(screen.getByText(/AWS credentials were invalid./i)).toBeInTheDocument();
    });

    it('renders clarification-needed tool status clearly', () => {
        render(
            <MessageBubble
                role="assistant"
                content="Which bucket should I inspect?"
                tool={{
                    used: true,
                    name: 's3_cloudwatch_report',
                    status: 'clarification-needed',
                    summary: 'Need a bucket before running the tool.'
                }}
            />
        );

        expect(screen.getByText(/needs input/i)).toBeInTheDocument();
        expect(screen.getByText(/Need a bucket before running the tool./i)).toBeInTheDocument();
    });

    it('renders partial-success tool status and legacy failed step titles clearly', () => {
        render(
            <MessageBubble
                role="assistant"
                content="The audit completed with some failures."
                tool={{
                    used: true,
                    name: 'aws_region_audit',
                    status: 'partial-success',
                    summary: 'AWS audit completed with failures: success_count=1, failure_count=2, skipped_count=0.'
                }}
                showTechnicalDetails
                toolResult={{
                    type: 'audit_summary',
                    status: 'partial-success',
                    failureCount: 2,
                    failedSteps: [
                        {
                            title: 'EC2 instances - us-east-1',
                            stderr_path: 'audit/aws-audit-run/stderr/ec2.stderr'
                        }
                    ]
                }}
            />
        );

        expect(screen.getByText(/completed with errors/i)).toBeInTheDocument();
        expect(screen.getByText(/EC2 instances - us-east-1/i)).toBeInTheDocument();
    });
});
