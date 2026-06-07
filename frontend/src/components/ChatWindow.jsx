/**
 * @fileoverview ChatWindow component that displays a list of message bubbles.
 * Uses forwardRef to allow the parent to control scrolling.
 */
import {forwardRef} from 'react';
import MessageBubble from './MessageBubble';

/**
 * ChatWindow component.
 *
 * @param {Object} props - Component props.
 * @param {Array<Object>} props.messages - Array of message objects to display.
 * @param {boolean} props.showTechnicalDetails - Whether to show technical details in message bubbles.
 * @param {Function} props.onPreviewArtifact - Callback to preview an artifact.
 * @param {Function} props.onListArtifacts - Callback to list artifacts for a run.
 * @param {Function} props.onCopyPath - Callback to copy a file path to clipboard.
 * @param {React.Ref} ref - Ref to the scrollable container.
 * @returns {React.JSX.Element} The rendered ChatWindow component.
 */
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
