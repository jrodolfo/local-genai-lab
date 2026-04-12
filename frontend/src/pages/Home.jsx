import { useEffect, useRef, useState } from 'react';
import { sendMessage, streamMessage } from '../api/chatApi';
import { deleteSession, exportSession, getSession, importSession, listSessions } from '../api/sessionApi';
import ChatWindow from '../components/ChatWindow';
import InputBox from '../components/InputBox';
import './Home.css';

const DEBUG_MODE_STORAGE_KEY = 'llm-pet-project.debug-mode';

function Home() {
  const importInputRef = useRef(null);
  const [messages, setMessages] = useState([]);
  const [sessionId, setSessionId] = useState(null);
  const [pendingTool, setPendingTool] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [sessionSearch, setSessionSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showTechnicalDetails, setShowTechnicalDetails] = useState(() => {
    if (typeof window === 'undefined') {
      return false;
    }
    return window.localStorage.getItem(DEBUG_MODE_STORAGE_KEY) === 'true';
  });

  useEffect(() => {
    const timerId = window.setTimeout(() => {
      loadSessions(sessionSearch);
    }, 250);

    return () => window.clearTimeout(timerId);
  }, [sessionSearch]);

  useEffect(() => {
    window.localStorage.setItem(DEBUG_MODE_STORAGE_KEY, String(showTechnicalDetails));
  }, [showTechnicalDetails]);

  const addMessage = (role, content, tool = null, metadata = null) => {
    setMessages((current) => [...current, { id: crypto.randomUUID(), role, content, tool, metadata }]);
  };

  const updateLastAssistant = (updater) => {
    setMessages((current) =>
      current.map((message, index) => {
        if (index !== current.length - 1 || message.role !== 'assistant') {
          return message;
        }
        return { ...message, content: updater(message.content) };
      })
    );
  };

  const updateLastAssistantDetails = ({ tool, metadata }) => {
    setMessages((current) =>
      current.map((message, index) => {
        if (index !== current.length - 1 || message.role !== 'assistant') {
          return message;
        }
        return {
          ...message,
          tool: tool ?? message.tool,
          metadata: metadata ?? message.metadata
        };
      })
    );
  };

  async function loadSessions(query = '') {
    try {
      const payload = await listSessions(query);
      setSessions(payload);
    } catch (err) {
      setError(err.message || 'Failed to load sessions.');
    }
  }

  const startNewChat = () => {
    setSessionId(null);
    setPendingTool(null);
    setMessages([]);
    setError('');
  };

  const handleImportClick = () => {
    importInputRef.current?.click();
  };

  const handleImportChange = async (event) => {
    const [file] = Array.from(event.target.files || []);
    event.target.value = '';
    if (!file) {
      return;
    }

    setError('');
    setLoading(true);
    try {
      const payload = await importSession(file);
      setSessionSearch('');
      await loadSessions('');
      await openSession(payload.sessionId);
    } catch (err) {
      setError(err.message || 'Failed to import session.');
    } finally {
      setLoading(false);
    }
  };

  const openSession = async (targetSessionId) => {
    setError('');
    setLoading(true);
    try {
      const payload = await getSession(targetSessionId);
      setSessionId(payload.sessionId);
      setPendingTool(payload.pendingTool || null);
      setMessages(
        payload.messages.map((message, index) => ({
          id: `${payload.sessionId}-${index}-${message.timestamp || index}`,
          role: message.role,
          content: message.content,
          tool: message.tool || null,
          metadata: message.metadata || null
        }))
      );
    } catch (err) {
      setError(err.message || 'Failed to load session.');
    } finally {
      setLoading(false);
    }
  };

  const removeSession = async (targetSessionId) => {
    setError('');
    setLoading(true);
    try {
      await deleteSession(targetSessionId);
      setSessions((current) => current.filter((session) => session.sessionId !== targetSessionId));
      if (sessionId === targetSessionId) {
        startNewChat();
      }
    } catch (err) {
      setError(err.message || 'Failed to delete session.');
    } finally {
      setLoading(false);
    }
  };

  const downloadSession = async (targetSessionId, format = 'json') => {
    setError('');
    setLoading(true);
    try {
      const { blob, filename } = await exportSession(targetSessionId, format);
      const objectUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(objectUrl);
    } catch (err) {
      setError(err.message || 'Failed to export session.');
    } finally {
      setLoading(false);
    }
  };

  const handleSend = async ({ message, model, streaming }) => {
    setError('');
    setLoading(true);
    addMessage('user', message);

    try {
      if (!streaming) {
        const payload = await sendMessage({ message, model, sessionId });
        setSessionId((current) => payload.sessionId || current);
        setPendingTool(payload.pendingTool || null);
        addMessage('assistant', payload.response || '(No response)', payload.tool || null, payload.metadata || null);
        await loadSessions(sessionSearch);
      } else {
        addMessage('assistant', '');
        await streamMessage({
          message,
          model,
          sessionId,
          onMetadata: (metadata) => {
            setSessionId((current) => metadata?.sessionId || current);
            setPendingTool(metadata?.pendingTool || null);
            updateLastAssistantDetails({ tool: metadata?.tool || null, metadata: metadata?.metadata || null });
          },
          onToken: (token) => {
            updateLastAssistant((current) => current + token);
          }
        });
        await loadSessions(sessionSearch);
      }
    } catch (err) {
      setError(err.message || 'Something went wrong.');
      addMessage('assistant', 'Error calling backend/Ollama. Check backend logs.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="home-page">
      <section className="chat-layout">
        <aside className="session-sidebar">
          <div className="session-sidebar-header">
            <h2>Sessions</h2>
            <div className="session-sidebar-actions">
              <input
                ref={importInputRef}
                type="file"
                accept="application/json,.json"
                className="session-import-input"
                aria-label="Import session file"
                onChange={handleImportChange}
              />
              <button type="button" onClick={handleImportClick} disabled={loading}>
                Import JSON
              </button>
              <button type="button" onClick={startNewChat} disabled={loading}>
                New chat
              </button>
            </div>
          </div>
          <div className="session-list">
            <input
              type="search"
              className="session-search"
              placeholder="Search sessions"
              value={sessionSearch}
              onChange={(event) => setSessionSearch(event.target.value)}
            />
            {sessions.length === 0 ? (
              <p className="session-empty">{sessionSearch ? 'No matching sessions.' : 'No saved chats yet.'}</p>
            ) : null}
            {sessions.map((session) => (
              <div
                key={session.sessionId}
                className={`session-item ${session.sessionId === sessionId ? 'active' : ''}`}
              >
                <button
                  type="button"
                  className="session-open"
                  onClick={() => openSession(session.sessionId)}
                  disabled={loading}
                >
                  <span className="session-title">{session.title}</span>
                  {session.summary ? <span className="session-summary">{session.summary}</span> : null}
                  <span className="session-meta">{new Date(session.updatedAt).toLocaleString()}</span>
                </button>
                <button
                  type="button"
                  className="session-export"
                  onClick={() => downloadSession(session.sessionId, 'json')}
                  disabled={loading}
                  aria-label={`Export session ${session.title}`}
                >
                  Export
                </button>
                <button
                  type="button"
                  className="session-export"
                  onClick={() => downloadSession(session.sessionId, 'markdown')}
                  disabled={loading}
                  aria-label={`Export markdown session ${session.title}`}
                >
                  Export MD
                </button>
                <button
                  type="button"
                  className="session-delete"
                  onClick={() => removeSession(session.sessionId)}
                  disabled={loading}
                  aria-label={`Delete session ${session.title}`}
                >
                  Delete
                </button>
              </div>
            ))}
          </div>
        </aside>

        <section className="chat-card">
        <header>
          <div>
            <h1>LLM Pet Project</h1>
            <p>React + Spring Boot + Ollama</p>
          </div>
          <label className="debug-toggle">
            <input
              type="checkbox"
              checked={showTechnicalDetails}
              onChange={(event) => setShowTechnicalDetails(event.target.checked)}
            />
            <span>show technical details</span>
          </label>
        </header>

        {error ? <div className="error-banner">{error}</div> : null}

        {pendingTool ? (
          <div className="pending-tool-banner">
            <strong>awaiting input for tool:</strong> {pendingTool.toolName}
            {pendingTool.missingFields?.length ? (
              <span>missing: {pendingTool.missingFields.join(', ')}</span>
            ) : null}
          </div>
        ) : null}

        <ChatWindow messages={messages} showTechnicalDetails={showTechnicalDetails} />

        <InputBox disabled={loading} onSend={handleSend} />
        </section>
      </section>
    </main>
  );
}

export default Home;
