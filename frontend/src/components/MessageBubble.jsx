import ToolResultCard from './ToolResultCard';

// Assistant replies are stored as plain text. The frontend renders a small, safe Markdown subset
// so model output stays readable without enabling raw HTML rendering.
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
        <div className="message-markdown">
          {renderMarkdownBlocks(content)}
        </div>
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
            {metadata.durationMs != null ? <span>provider duration: {formatDuration(metadata.durationMs)}</span> : null}
            {metadata.providerLatencyMs != null ? <span>provider latency: {formatDuration(metadata.providerLatencyMs)}</span> : null}
            {metadata.backendDurationMs != null ? <span>backend total: {formatDuration(metadata.backendDurationMs)}</span> : null}
            {metadata.uiWaitMs != null ? <span>ui wait: {formatDuration(metadata.uiWaitMs)}</span> : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function formatDuration(totalMs) {
  const durationMs = Math.max(0, totalMs ?? 0);
  const minutes = Math.floor(durationMs / 60000);
  const seconds = Math.floor((durationMs % 60000) / 1000);
  const milliseconds = durationMs % 1000;
  const parts = [];

  if (minutes > 0) {
    parts.push(`${minutes} m`);
  }
  if (seconds > 0 || minutes > 0) {
    parts.push(`${seconds} s`);
  }
  parts.push(`${milliseconds} ms`);

  return parts.join(' ');
}

function renderMarkdownBlocks(content = '') {
  const lines = content.replace(/\r\n/g, '\n').split('\n');
  const blocks = [];
  let index = 0;
  let key = 0;

  while (index < lines.length) {
    const line = lines[index];
    const trimmed = line.trim();

    if (!trimmed) {
      index += 1;
      continue;
    }

    if (trimmed.startsWith('```')) {
      const language = trimmed.slice(3).trim();
      const codeLines = [];
      index += 1;
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        codeLines.push(lines[index]);
        index += 1;
      }
      if (index < lines.length && lines[index].trim().startsWith('```')) {
        index += 1;
      }
      blocks.push(
        <div key={`code-block-${key++}`} className="message-code-block">
          {language ? <span className="message-code-language">{language}</span> : null}
          <pre><code>{codeLines.join('\n')}</code></pre>
        </div>
      );
      continue;
    }

    if (/^-{3,}$/.test(trimmed)) {
      blocks.push(<hr key={`rule-${key++}`} className="message-rule" />);
      index += 1;
      continue;
    }

    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      const level = Math.min(6, headingMatch[1].length);
      const HeadingTag = `h${level}`;
      blocks.push(
        <HeadingTag key={`heading-${key++}`} className={`message-heading message-heading-${level}`}>
          {renderInlineMarkdown(headingMatch[2])}
        </HeadingTag>
      );
      index += 1;
      continue;
    }

    const orderedMatch = trimmed.match(/^(\d+)\.\s+(.+)$/);
    if (orderedMatch) {
      const items = [];
      while (index < lines.length) {
        const current = lines[index].trim();
        const match = current.match(/^(\d+)\.\s+(.+)$/);
        if (!match) {
          break;
        }
        items.push(match[2]);
        index += 1;
      }
      blocks.push(
        <ol key={`ol-${key++}`} className="message-list message-list-ordered">
          {items.map((item, itemIndex) => (
            <li key={`ol-item-${itemIndex}`}>{renderInlineMarkdown(item)}</li>
          ))}
        </ol>
      );
      continue;
    }

    const unorderedMatch = trimmed.match(/^[-*]\s+(.+)$/);
    if (unorderedMatch) {
      const items = [];
      while (index < lines.length) {
        const current = lines[index].trim();
        const match = current.match(/^[-*]\s+(.+)$/);
        if (!match) {
          break;
        }
        items.push(match[1]);
        index += 1;
      }
      blocks.push(
        <ul key={`ul-${key++}`} className="message-list message-list-unordered">
          {items.map((item, itemIndex) => (
            <li key={`ul-item-${itemIndex}`}>{renderInlineMarkdown(item)}</li>
          ))}
        </ul>
      );
      continue;
    }

    const paragraphLines = [line];
    index += 1;
    while (index < lines.length) {
      const current = lines[index];
      const currentTrimmed = current.trim();
      if (
        !currentTrimmed ||
        currentTrimmed.startsWith('```') ||
        /^-{3,}$/.test(currentTrimmed) ||
        /^(#{1,6})\s+(.+)$/.test(currentTrimmed) ||
        /^(\d+)\.\s+(.+)$/.test(currentTrimmed) ||
        /^[-*]\s+(.+)$/.test(currentTrimmed)
      ) {
        break;
      }
      paragraphLines.push(current);
      index += 1;
    }
    blocks.push(
      <p key={`paragraph-${key++}`} className="message-paragraph">
        {renderInlineMarkdown(paragraphLines.join('\n'))}
      </p>
    );
  }

  if (blocks.length === 0) {
    return <p className="message-paragraph">{renderInlineMarkdown(content)}</p>;
  }

  return blocks;
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
