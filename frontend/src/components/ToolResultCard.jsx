function ToolResultCard({ toolResult, onPreviewArtifact, onListArtifacts, onCopyPath }) {
  if (!toolResult?.type) {
    return null;
  }

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
        <span className="tool-result-title">reports</span>
        <span>type: {toolResult.reportType || 'all'}</span>
        {reports.length === 0 ? <span>no reports found.</span> : null}
        {reports.map((report) => (
          <div key={report.run_dir || report.summary_json || report.report_txt} className="tool-result-item">
            <span>{report.report_type || 'report'}</span>
            {report.created_at ? <span>{report.created_at}</span> : null}
            {report.run_dir ? <span>{report.run_dir}</span> : null}
            {renderActions([
              report.summary_json ? (
                <button key="summary" type="button" onClick={() => onPreviewArtifact?.(report.summary_json, 'summary preview')}>
                  View summary
                </button>
              ) : null,
              report.report_txt ? (
                <button key="report" type="button" onClick={() => onPreviewArtifact?.(report.report_txt, 'report preview')}>
                  View report
                </button>
              ) : null,
              report.run_dir ? (
                <button key="files" type="button" onClick={() => onListArtifacts?.(report.run_dir, 'artifact files')}>
                  List files
                </button>
              ) : null,
              report.run_dir ? (
                <button key="copy" type="button" onClick={() => onCopyPath?.(report.run_dir)}>
                  Copy run dir
                </button>
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
        <span className="tool-result-title">report summary</span>
        {toolResult.reportType ? <span>type: {toolResult.reportType}</span> : null}
        {toolResult.runDir ? <span>run dir: {toolResult.runDir}</span> : null}
        {summary.success_count != null ? <span>success: {summary.success_count}</span> : null}
        {summary.failure_count != null ? <span>failures: {summary.failure_count}</span> : null}
        {toolResult.reportPreview ? <span>{toolResult.reportPreview}</span> : null}
        {renderActions([
          toolResult.summaryPath ? (
            <button key="summary" type="button" onClick={() => onPreviewArtifact?.(toolResult.summaryPath, 'summary preview')}>
              View summary
            </button>
          ) : null,
          toolResult.reportPath ? (
            <button key="report" type="button" onClick={() => onPreviewArtifact?.(toolResult.reportPath, 'report preview')}>
              View report
            </button>
          ) : null,
          toolResult.runDir ? (
            <button key="files" type="button" onClick={() => onListArtifacts?.(toolResult.runDir, 'artifact files')}>
              List files
            </button>
          ) : null,
          toolResult.runDir ? (
            <button key="copy" type="button" onClick={() => onCopyPath?.(toolResult.runDir)}>
              Copy run dir
            </button>
          ) : null
        ])}
      </div>
    );
  }

  if (toolResult.type === 'audit_summary') {
    const failedSteps = Array.isArray(toolResult.failedSteps) ? toolResult.failedSteps : [];
    return (
      <div className="tool-result-card">
        <span className="tool-result-title">aws audit</span>
        {toolResult.accountId ? <span>account: {toolResult.accountId}</span> : null}
        {toolResult.runDir ? <span>run dir: {toolResult.runDir}</span> : null}
        <span>success: {toolResult.successCount ?? 0}</span>
        <span>failures: {toolResult.failureCount ?? 0}</span>
        <span>skipped: {toolResult.skippedCount ?? 0}</span>
        {Array.isArray(toolResult.selectedRegions) && toolResult.selectedRegions.length > 0 ? (
          <span>regions: {toolResult.selectedRegions.join(', ')}</span>
        ) : null}
        {Array.isArray(toolResult.selectedServices) && toolResult.selectedServices.length > 0 ? (
          <span>services: {toolResult.selectedServices.join(', ')}</span>
        ) : null}
        {renderActions([
          toolResult.summaryPath ? (
            <button key="summary" type="button" onClick={() => onPreviewArtifact?.(toolResult.summaryPath, 'summary preview')}>
              View summary
            </button>
          ) : null,
          toolResult.reportPath ? (
            <button key="report" type="button" onClick={() => onPreviewArtifact?.(toolResult.reportPath, 'report preview')}>
              View report
            </button>
          ) : null,
          toolResult.runDir ? (
            <button key="files" type="button" onClick={() => onListArtifacts?.(toolResult.runDir, 'artifact files')}>
              List files
            </button>
          ) : null,
          toolResult.runDir ? (
            <button key="copy" type="button" onClick={() => onCopyPath?.(toolResult.runDir)}>
              Copy run dir
            </button>
          ) : null
        ])}
        {failedSteps.length > 0 ? (
          <div className="tool-result-item">
            <span>failed steps:</span>
            {failedSteps.map((step) => (
              <div key={step.step || step.stderr_path} className="tool-result-item">
                <span>{step.step || 'unknown step'}</span>
                {step.stderr_path ? (
                  <button type="button" onClick={() => onPreviewArtifact?.(step.stderr_path, 'failed step stderr')}>
                    View stderr
                  </button>
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
        <span className="tool-result-title">s3 cloudwatch report</span>
        {toolResult.bucket ? <span>bucket: {toolResult.bucket}</span> : null}
        {toolResult.runDir ? <span>run dir: {toolResult.runDir}</span> : null}
        <span>success: {toolResult.successCount ?? 0}</span>
        <span>failures: {toolResult.failureCount ?? 0}</span>
        <span>skipped: {toolResult.skippedCount ?? 0}</span>
        {renderActions([
          toolResult.summaryPath ? (
            <button key="summary" type="button" onClick={() => onPreviewArtifact?.(toolResult.summaryPath, 'summary preview')}>
              View summary
            </button>
          ) : null,
          toolResult.reportPath ? (
            <button key="report" type="button" onClick={() => onPreviewArtifact?.(toolResult.reportPath, 'report preview')}>
              View report
            </button>
          ) : null,
          toolResult.runDir ? (
            <button key="files" type="button" onClick={() => onListArtifacts?.(toolResult.runDir, 'artifact files')}>
              List files
            </button>
          ) : null,
          toolResult.runDir ? (
            <button key="copy" type="button" onClick={() => onCopyPath?.(toolResult.runDir)}>
              Copy run dir
            </button>
          ) : null
        ])}
      </div>
    );
  }

  return null;
}

export default ToolResultCard;
