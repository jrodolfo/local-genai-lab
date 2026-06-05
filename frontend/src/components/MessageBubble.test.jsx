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

    it('renders S3 bucket discovery results with a concrete next prompt', () => {
        render(
            <MessageBubble
                role="assistant"
                content="I found your S3 buckets."
                tool={{used: true, name: 'aws_region_audit', status: 'success', summary: 'S3 bucket discovery completed.'}}
                toolResult={{
                    type: 'audit_summary',
                    selectedServices: ['s3'],
                    bucketNames: ['first-bucket', 'second-bucket'],
                    runDir: 'audit/aws-audit-2026-06-05_14-14-12',
                    successCount: 1,
                    failureCount: 0,
                    skippedCount: 36,
                    summaryPath: 'audit/aws-audit-2026-06-05_14-14-12/summary.json',
                    reportPath: 'audit/aws-audit-2026-06-05_14-14-12/report.txt'
                }}
            />
        );

        expect(screen.getByText('S3 bucket discovery')).toBeInTheDocument();
        expect(screen.getByText('Accessible buckets')).toBeInTheDocument();
        expect(screen.getByText('first-bucket')).toBeInTheDocument();
        expect(screen.getByText('second-bucket')).toBeInTheDocument();
        expect(screen.getByText(/run an S3 report for first-bucket for the last month/i)).toBeInTheDocument();
        expect(screen.getByRole('button', {name: /open report/i})).toBeInTheDocument();
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
                    uiWaitMs: 490
                }}
            />
        );

        expect(screen.getByText(/Bedrock · amazon\.nova-lite-v1:0/i)).toBeInTheDocument();
        expect(screen.getByText(/technical details/i)).toBeInTheDocument();
        expect(screen.getByText(/tool completed successfully; final wording still depends on the selected model/i)).toBeInTheDocument();
        expect(screen.getByText(/provider: bedrock/i)).toBeInTheDocument();
        expect(screen.getByText(/model: amazon.nova-lite-v1:0/i)).toBeInTheDocument();
        expect(screen.getByText(/stop reason: end_turn/i)).toBeInTheDocument();
        expect(screen.getByText(/tokens: 12 in \/ 34 out \/ 46 total/i)).toBeInTheDocument();
        expect(screen.getByText(/provider duration: 412 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/provider latency: 321 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/backend total: 450 ms/i)).toBeInTheDocument();
        expect(screen.getByText(/ui wait: 490 ms/i)).toBeInTheDocument();
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
});
