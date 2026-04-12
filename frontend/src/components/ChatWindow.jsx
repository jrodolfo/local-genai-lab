import MessageBubble from './MessageBubble';

function ChatWindow({ messages, showTechnicalDetails, onPreviewArtifact, onListArtifacts, onCopyPath }) {
  if (messages.length === 0) {
    return (
      <div className="chat-window empty-state">
        <p>Ask something to start a conversation.</p>
      </div>
    );
  }

  return (
    <div className="chat-window">
      {messages.map((message) => (
        <MessageBubble
          key={message.id}
          role={message.role}
          content={message.content}
          tool={message.tool}
          toolResult={message.toolResult}
          metadata={message.metadata}
          showTechnicalDetails={showTechnicalDetails}
          onPreviewArtifact={onPreviewArtifact}
          onListArtifacts={onListArtifacts}
          onCopyPath={onCopyPath}
        />
      ))}
    </div>
  );
}

export default ChatWindow;
