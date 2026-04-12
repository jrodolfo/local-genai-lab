import ToolResultCard from './ToolResultCard';

function MessageBubble({
  role,
  content,
  tool,
  toolResult,
  metadata,
  showTechnicalDetails = false,
  onPreviewArtifact,
  onListArtifacts,
  onCopyPath
}) {
  const isUser = role === 'user';
  const showTool = !isUser && tool?.used;
  const showMetadata = !isUser && showTechnicalDetails && metadata && (metadata.provider || metadata.modelId);
  const showToolResult = !isUser && toolResult?.type;

  return (
    <div className={`message-row ${isUser ? 'user' : 'assistant'}`}>
      <div className="message-bubble">
        <p>{content}</p>
        {showTool ? (
          <div className="tool-provenance">
            <span>used tool: {tool.name}</span>
            {tool.status ? <span>status: {tool.status}</span> : null}
            {tool.summary ? <span>{tool.summary}</span> : null}
          </div>
        ) : null}
        {showToolResult ? (
          <ToolResultCard
            toolResult={toolResult}
            onPreviewArtifact={onPreviewArtifact}
            onListArtifacts={onListArtifacts}
            onCopyPath={onCopyPath}
          />
        ) : null}
        {showMetadata ? (
          <div className="provider-metadata">
            <span className="provider-metadata-title">technical details</span>
            {metadata.provider ? <span>provider: {metadata.provider}</span> : null}
            {metadata.modelId ? <span>model: {metadata.modelId}</span> : null}
            {metadata.stopReason ? <span>stop reason: {metadata.stopReason}</span> : null}
            {metadata.inputTokens != null || metadata.outputTokens != null || metadata.totalTokens != null ? (
              <span>
                tokens: {metadata.inputTokens ?? '?'} in / {metadata.outputTokens ?? '?'} out / {metadata.totalTokens ?? '?'} total
              </span>
            ) : null}
            {metadata.durationMs != null ? <span>duration: {metadata.durationMs} ms</span> : null}
            {metadata.providerLatencyMs != null ? <span>provider latency: {metadata.providerLatencyMs} ms</span> : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}

export default MessageBubble;
