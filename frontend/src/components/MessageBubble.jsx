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
  const showToolVarianceHint = !isUser && showTechnicalDetails && tool?.used && tool?.status === 'success';

  return (
    <div className={`message-row ${isUser ? 'user' : 'assistant'}`}>
      <div className="message-bubble">
        <p>{renderInlineMarkdown(content)}</p>
        {showTool ? (
          <div className="tool-provenance">
            <span>used tool: {tool.name}</span>
            {tool.status ? <span>status: {tool.status}</span> : null}
            {tool.summary ? <span>{tool.summary}</span> : null}
          </div>
        ) : null}
        {showToolVarianceHint ? (
          <div className="tool-variance-hint">
            <span>tool completed successfully; final wording still depends on the selected model</span>
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

function renderInlineMarkdown(content = '') {
  const parts = [];
  const pattern = /(\*\*[^*\n][\s\S]*?\*\*|`[^`\n]+`)/g;
  let lastIndex = 0;
  let match;
  let key = 0;

  while ((match = pattern.exec(content)) !== null) {
    if (match.index > lastIndex) {
      parts.push(content.slice(lastIndex, match.index));
    }

    const token = match[0];
    if (token.startsWith('**') && token.endsWith('**')) {
      const boldText = token.slice(2, -2);
      parts.push(<strong key={`bold-${key++}`}>{boldText}</strong>);
    } else if (token.startsWith('`') && token.endsWith('`')) {
      const codeText = token.slice(1, -1);
      parts.push(<code key={`code-${key++}`}>{codeText}</code>);
    } else {
      parts.push(token);
    }

    lastIndex = pattern.lastIndex;
  }

  if (lastIndex < content.length) {
    parts.push(content.slice(lastIndex));
  }

  return parts;
}

export default MessageBubble;
