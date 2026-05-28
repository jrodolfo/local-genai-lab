function ToolResultCard({toolResult, onPreviewArtifact, onListArtifacts, onCopyPath}) {
    if (!toolResult?.type) {
        return null;
    }

    const actionButton = (key, label, onClick) => (
        <button key={key} type="button" onClick={onClick}>
            {label}
        </button>
    );

    const renderActions = (actions) => {
        const visibleActions = actions.filter(Boolean);
        if (visibleActions.length === 0) {
            return null;
        }

        return <div className="tool-result-actions">{visibleActions}</div>;
    };

    if (toolResult.type === 'report_list') {
        const reports = Array.isArray(toolResult.reports) ? toolResult.reports : [];
        return (
            <div className="tool-result-card">
                <span className="tool-result-title">Recent reports</span>
                <span>Report type: {toolResult.reportType || 'all'}</span>
                {reports.length === 0 ? <span>no reports found.</span> : null}
                {reports.map((report) => (
                    <div key={report.run_dir || report.summary_json || report.report_txt} className="tool-result-item">
                        <span className="tool-result-item-title">{report.report_type || 'report'}</span>
                        {report.created_at ? <span>Created: {report.created_at}</span> : null}
                        {report.run_dir ? <span>Run directory: {report.run_dir}</span> : null}
                        {renderActions([
                            report.summary_json ? (
                                actionButton('summary', 'Open summary', () => onPreviewArtifact?.(report.summary_json, 'summary preview'))
                            ) : null,
                            report.report_txt ? (
                                actionButton('report', 'Open report', () => onPreviewArtifact?.(report.report_txt, 'report preview'))
                            ) : null,
                            report.run_dir ? (
                                actionButton('files', 'Show files', () => onListArtifacts?.(report.run_dir, 'artifact files'))
                            ) : null,
                            report.run_dir ? (
                                actionButton('copy', 'Copy run directory', () => onCopyPath?.(report.run_dir))
                            ) : null
                        ])}
                    </div>
                ))}
            </div>
        );
    }

    if (toolResult.type === 'report_summary') {
        const summary = toolResult.summary || {};
        return (
            <div className="tool-result-card">
                <span className="tool-result-title">Latest report summary</span>
                {toolResult.reportType ? <span>Report type: {toolResult.reportType}</span> : null}
                {toolResult.runDir ? <span>Run directory: {toolResult.runDir}</span> : null}
                {summary.success_count != null ? <span>Success: {summary.success_count}</span> : null}
                {summary.failure_count != null ? <span>Failures: {summary.failure_count}</span> : null}
                {toolResult.reportPreview ? <span>{toolResult.reportPreview}</span> : null}
                {renderActions([
                    toolResult.summaryPath ? (
                        actionButton('summary', 'Open summary', () => onPreviewArtifact?.(toolResult.summaryPath, 'summary preview'))
                    ) : null,
                    toolResult.reportPath ? (
                        actionButton('report', 'Open report', () => onPreviewArtifact?.(toolResult.reportPath, 'report preview'))
                    ) : null,
                    toolResult.runDir ? (
                        actionButton('files', 'Show files', () => onListArtifacts?.(toolResult.runDir, 'artifact files'))
                    ) : null,
                    toolResult.runDir ? (
                        actionButton('copy', 'Copy run directory', () => onCopyPath?.(toolResult.runDir))
                    ) : null
                ])}
            </div>
        );
    }

    if (toolResult.type === 'audit_summary') {
        const failedSteps = Array.isArray(toolResult.failedSteps) ? toolResult.failedSteps : [];
        return (
            <div className="tool-result-card">
                <span className="tool-result-title">AWS audit result</span>
                {toolResult.accountId ? <span>Account: {toolResult.accountId}</span> : null}
                {toolResult.runDir ? <span>Run directory: {toolResult.runDir}</span> : null}
                <span>Success: {toolResult.successCount ?? 0}</span>
                <span>Failures: {toolResult.failureCount ?? 0}</span>
                <span>Skipped: {toolResult.skippedCount ?? 0}</span>
                {Array.isArray(toolResult.selectedRegions) && toolResult.selectedRegions.length > 0 ? (
                    <span>Regions: {toolResult.selectedRegions.join(', ')}</span>
                ) : null}
                {Array.isArray(toolResult.selectedServices) && toolResult.selectedServices.length > 0 ? (
                    <span>Services: {toolResult.selectedServices.join(', ')}</span>
                ) : null}
                {renderActions([
                    toolResult.summaryPath ? (
                        actionButton('summary', 'Open summary', () => onPreviewArtifact?.(toolResult.summaryPath, 'summary preview'))
                    ) : null,
                    toolResult.reportPath ? (
                        actionButton('report', 'Open report', () => onPreviewArtifact?.(toolResult.reportPath, 'report preview'))
                    ) : null,
                    toolResult.runDir ? (
                        actionButton('files', 'Show files', () => onListArtifacts?.(toolResult.runDir, 'artifact files'))
                    ) : null,
                    toolResult.runDir ? (
                        actionButton('copy', 'Copy run directory', () => onCopyPath?.(toolResult.runDir))
                    ) : null
                ])}
                {failedSteps.length > 0 ? (
                    <div className="tool-result-item">
                        <span className="tool-result-item-title">Failed steps</span>
                        {failedSteps.map((step) => (
                            <div key={step.step || step.stderr_path} className="tool-result-item">
                                <span>{step.step || 'unknown step'}</span>
                                {step.stderr_path ? (
                                    actionButton(step.stderr_path, 'Open stderr', () => onPreviewArtifact?.(step.stderr_path, 'failed step stderr'))
                                ) : null}
                            </div>
                        ))}
                    </div>
                ) : null}
            </div>
        );
    }

    if (toolResult.type === 's3_report_summary') {
        return (
            <div className="tool-result-card">
                <span className="tool-result-title">S3 CloudWatch report</span>
                {toolResult.bucket ? <span>Bucket: {toolResult.bucket}</span> : null}
                {toolResult.runDir ? <span>Run directory: {toolResult.runDir}</span> : null}
                <span>Success: {toolResult.successCount ?? 0}</span>
                <span>Failures: {toolResult.failureCount ?? 0}</span>
                <span>Skipped: {toolResult.skippedCount ?? 0}</span>
                {renderActions([
                    toolResult.summaryPath ? (
                        actionButton('summary', 'Open summary', () => onPreviewArtifact?.(toolResult.summaryPath, 'summary preview'))
                    ) : null,
                    toolResult.reportPath ? (
                        actionButton('report', 'Open report', () => onPreviewArtifact?.(toolResult.reportPath, 'report preview'))
                    ) : null,
                    toolResult.runDir ? (
                        actionButton('files', 'Show files', () => onListArtifacts?.(toolResult.runDir, 'artifact files'))
                    ) : null,
                    toolResult.runDir ? (
                        actionButton('copy', 'Copy run directory', () => onCopyPath?.(toolResult.runDir))
                    ) : null
                ])}
            </div>
        );
    }

    return null;
}

export default ToolResultCard;
