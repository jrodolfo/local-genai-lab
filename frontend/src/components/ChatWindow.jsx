import {forwardRef} from 'react';
import MessageBubble from './MessageBubble';

const ChatWindow = forwardRef(function ChatWindow(
    {messages, showTechnicalDetails, onPreviewArtifact, onListArtifacts, onCopyPath},
    ref
) {
    if (messages.length === 0) {
        return (
            <div ref={ref} className="chat-window empty-state">
                <p>Ask something to start a conversation.</p>
            </div>
        );
    }

    return (
        <div ref={ref} className="chat-window">
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
});

export default ChatWindow;
