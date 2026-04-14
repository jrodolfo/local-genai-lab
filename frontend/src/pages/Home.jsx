import { useEffect, useRef, useState } from 'react';
import { listArtifacts, previewArtifact } from '../api/artifactApi';
import { sendMessage, streamMessage } from '../api/chatApi';
import { listAvailableModels } from '../api/modelApi';
import { deleteSession, exportSession, getSession, importSession, listSessions } from '../api/sessionApi';
import ChatWindow from '../components/ChatWindow';
import InputBox from '../components/InputBox';
import './Home.css';

const DEBUG_MODE_STORAGE_KEY = 'local-genai-lab.debug-mode';

function Home() {
  const importInputRef = useRef(null);
  const chatWindowRef = useRef(null);
  const [messages, setMessages] = useState([]);
  const [sessionId, setSessionId] = useState(null);
  const [pendingTool, setPendingTool] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [sessionSearch, setSessionSearch] = useState('');
  const [providerFilter, setProviderFilter] = useState('');
  const [toolUsageFilter, setToolUsageFilter] = useState('');
  const [pendingOnly, setPendingOnly] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingMessage, setLoadingMessage] = useState('');
  const [loadingStartedAt, setLoadingStartedAt] = useState(null);
  const [loadingElapsedSeconds, setLoadingElapsedSeconds] = useState(0);
  const [availableModels, setAvailableModels] = useState([]);
  const [selectedModel, setSelectedModel] = useState('');
  const [activeProvider, setActiveProvider] = useState('ollama');
  const [modelsLoading, setModelsLoading] = useState(true);
  const [modelsLoadFailed, setModelsLoadFailed] = useState(false);
  const [error, setError] = useState('');
  const [artifactFiles, setArtifactFiles] = useState([]);
  const [artifactPreview, setArtifactPreview] = useState(null);
  const [artifactPanelTitle, setArtifactPanelTitle] = useState('');
  const [showTechnicalDetails, setShowTechnicalDetails] = useState(() => {
    if (typeof window === 'undefined') {
      return false;
    }
    return window.localStorage.getItem(DEBUG_MODE_STORAGE_KEY) === 'true';
  });

  useEffect(() => {
    const timerId = window.setTimeout(() => {
      loadSessions({
        query: sessionSearch,
        provider: providerFilter,
        toolUsage: toolUsageFilter,
        pending: pendingOnly
      });
    }, 250);

    return () => window.clearTimeout(timerId);
  }, [sessionSearch, providerFilter, toolUsageFilter, pendingOnly]);

  useEffect(() => {
    window.localStorage.setItem(DEBUG_MODE_STORAGE_KEY, String(showTechnicalDetails));
  }, [showTechnicalDetails]);

  useEffect(() => {
    if (!loadingStartedAt) {
      setLoadingElapsedSeconds(0);
      return undefined;
    }

    const updateElapsed = () => {
      setLoadingElapsedSeconds(Math.max(0, Math.floor((Date.now() - loadingStartedAt) / 1000)));
    };

    updateElapsed();
    const intervalId = window.setInterval(updateElapsed, 1000);
    return () => window.clearInterval(intervalId);
  }, [loadingStartedAt]);

  useEffect(() => {
    loadAvailableModels();
  }, []);

  useEffect(() => {
    const chatWindow = chatWindowRef.current;
    if (!chatWindow) {
      return;
    }
    chatWindow.scrollTo({
      top: chatWindow.scrollHeight,
      behavior: 'auto'
    });
  }, [messages, loading, loadingMessage]);

  const addMessage = (role, content, tool = null, toolResult = null, metadata = null) => {
    setMessages((current) => [...current, { id: crypto.randomUUID(), role, content, tool, toolResult, metadata }]);
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

  const updateLastAssistantDetails = ({ tool, toolResult, metadata }) => {
    setMessages((current) =>
      current.map((message, index) => {
        if (index !== current.length - 1 || message.role !== 'assistant') {
          return message;
        }
        return {
          ...message,
          tool: tool ?? message.tool,
          toolResult: toolResult ?? message.toolResult,
          metadata: metadata ?? message.metadata
        };
      })
    );
  };

  const applyUiWaitToLastAssistant = (uiWaitMs) => {
    setMessages((current) =>
      current.map((message, index) => {
        if (index !== current.length - 1 || message.role !== 'assistant') {
          return message;
        }
        return {
          ...message,
          metadata: mergeProviderMetadata(message.metadata, { uiWaitMs })
        };
      })
    );
  };

  async function loadSessions(filters = {}) {
    try {
      const payload = await listSessions(filters);
      setSessions(payload);
    } catch (err) {
      setError(err.message || 'Failed to load sessions.');
    }
  }

  async function loadAvailableModels() {
    try {
      setModelsLoading(true);
      setModelsLoadFailed(false);
      const payload = await listAvailableModels();
      const models = Array.isArray(payload.models) ? payload.models : [];
      const defaultModel = payload.defaultModel && models.includes(payload.defaultModel) ? payload.defaultModel : '';
      setActiveProvider(payload.provider || 'ollama');
      setAvailableModels(models);
      setSelectedModel((current) => {
        if (current && models.includes(current)) {
          return current;
        }
        if (defaultModel) {
          return defaultModel;
        }
        return models[0] || '';
      });
    } catch (err) {
      setModelsLoadFailed(true);
      setError(err.message || 'Failed to load available models.');
      setAvailableModels([]);
      setSelectedModel('');
    } finally {
      setModelsLoading(false);
    }
  }

  const startNewChat = () => {
    setSessionId(null);
    setPendingTool(null);
    setMessages([]);
    setArtifactFiles([]);
    setArtifactPreview(null);
    setArtifactPanelTitle('');
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
      setProviderFilter('');
      setToolUsageFilter('');
      setPendingOnly(false);
      await loadSessions({});
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
      setArtifactFiles([]);
      setArtifactPreview(null);
      setArtifactPanelTitle('');
      setMessages(
        payload.messages.map((message, index) => ({
          id: `${payload.sessionId}-${index}-${message.timestamp || index}`,
          role: message.role,
          content: message.content,
          tool: message.tool || null,
          toolResult: message.toolResult || null,
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

  const handleListArtifacts = async (runDir, title = 'artifact files') => {
    setError('');
    setLoading(true);
    try {
      const payload = await listArtifacts(runDir);
      setArtifactPanelTitle(title);
      setArtifactFiles(payload);
      setArtifactPreview(null);
    } catch (err) {
      setError(err.message || 'Failed to list artifact files.');
    } finally {
      setLoading(false);
    }
  };

  const handlePreviewArtifact = async (path, title = 'artifact preview') => {
    setError('');
    setLoading(true);
    try {
      const payload = await previewArtifact(path);
      setArtifactPanelTitle(title);
      setArtifactPreview(payload);
    } catch (err) {
      setError(err.message || 'Failed to preview artifact.');
    } finally {
      setLoading(false);
    }
  };

  const handleCopyPath = async (path) => {
    try {
      await navigator.clipboard.writeText(path);
    } catch (err) {
      setError(err.message || 'Failed to copy artifact path.');
    }
  };

  const handleSend = async ({ message, model, streaming }) => {
    const requestStartedAt = Date.now();
    setError('');
    setLoading(true);
    setLoadingMessage(streaming ? 'Waiting for streamed response...' : 'Waiting for response...');
    setLoadingStartedAt(Date.now());
    addMessage('user', message);

    try {
      if (!streaming) {
        const payload = await sendMessage({ message, model, sessionId });
        setSessionId((current) => payload.sessionId || current);
        setPendingTool(payload.pendingTool || null);
        addMessage(
          'assistant',
          payload.response || '(No response)',
          payload.tool || null,
          payload.toolResult || null,
          payload.metadata || null
        );
        await loadSessions({
          query: sessionSearch,
          provider: providerFilter,
          toolUsage: toolUsageFilter,
          pending: pendingOnly
        });
        applyUiWaitToLastAssistant(Date.now() - requestStartedAt);
      } else {
        addMessage('assistant', '');
        await streamMessage({
          message,
          model,
          sessionId,
          onEvent: (event) => {
            if (event.type === 'start' || event.type === 'complete') {
              setSessionId((current) => event?.sessionId || current);
              setPendingTool(event?.pendingTool || null);
              updateLastAssistantDetails({
                tool: event?.tool || null,
                toolResult: event?.toolResult || null,
                metadata: event?.metadata || null
              });
              return;
            }

            if (event.type === 'delta') {
              updateLastAssistant((current) => current + (event.text || ''));
            }
          }
        });
        await loadSessions({
          query: sessionSearch,
          provider: providerFilter,
          toolUsage: toolUsageFilter,
          pending: pendingOnly
        });
        applyUiWaitToLastAssistant(Date.now() - requestStartedAt);
      }
    } catch (err) {
      setError(err.message || 'Something went wrong.');
      addMessage('assistant', 'Error calling backend/Ollama. Check backend logs.');
    } finally {
      setLoading(false);
      setLoadingMessage('');
      setLoadingStartedAt(null);
    }
  };

  const loadingStatusMessage = loadingMessage
    ? `${loadingMessage} ${formatElapsedTime(loadingElapsedSeconds)}`
    : '';

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
            <div className="session-filters">
              <select
                aria-label="Provider filter"
                value={providerFilter}
                onChange={(event) => setProviderFilter(event.target.value)}
              >
                <option value="">All providers</option>
                <option value="ollama">Ollama</option>
                <option value="bedrock">Bedrock</option>
              </select>
              <select
                aria-label="Tool usage filter"
                value={toolUsageFilter}
                onChange={(event) => setToolUsageFilter(event.target.value)}
              >
                <option value="">All sessions</option>
                <option value="used">Used tools</option>
                <option value="unused">No tools</option>
              </select>
              <label className="session-filter-toggle">
                <input
                  type="checkbox"
                  checked={pendingOnly}
                  onChange={(event) => setPendingOnly(event.target.checked)}
                />
                <span>Pending only</span>
              </label>
            </div>
            {sessions.length === 0 ? (
              <p className="session-empty">{sessionSearch || providerFilter || toolUsageFilter || pendingOnly ? 'No matching sessions.' : 'No saved chats yet.'}</p>
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
                  Export JSON
                </button>
                <button
                  type="button"
                  className="session-export"
                  onClick={() => downloadSession(session.sessionId, 'markdown')}
                  disabled={loading}
                  aria-label={`Export markdown session ${session.title}`}
                >
                  Export Markdown
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
            <h1>Local GenAI Lab</h1>
            <p>{`React + Spring Boot + provider: ${formatProviderName(activeProvider)}`}</p>
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

        {artifactFiles.length > 0 || artifactPreview ? (
          <section className="artifact-panel">
            <div className="artifact-panel-header">
              <div>
                <strong>{artifactPanelTitle || 'artifact inspector'}</strong>
                {artifactPreview?.relativePath ? <span>{artifactPreview.relativePath}</span> : null}
              </div>
              <button
                type="button"
                onClick={() => {
                  setArtifactFiles([]);
                  setArtifactPreview(null);
                  setArtifactPanelTitle('');
                }}
              >
                Close
              </button>
            </div>
            {artifactFiles.length > 0 ? (
              <div className="artifact-file-list">
                {artifactFiles.map((file) => (
                  <div key={file.path} className="artifact-file-item">
                    <span>{file.relativePath}</span>
                    <div className="artifact-file-actions">
                      {file.previewable ? (
                        <button type="button" onClick={() => handlePreviewArtifact(file.path, 'artifact preview')}>
                          Preview
                        </button>
                      ) : null}
                      <button type="button" onClick={() => handleCopyPath(file.path)}>
                        Copy path
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : null}
            {artifactPreview ? (
              <div className="artifact-preview">
                <div className="artifact-preview-meta">
                  <span>{artifactPreview.fileName}</span>
                  <span>{artifactPreview.contentType}</span>
                  <span>{artifactPreview.size} bytes</span>
                  {artifactPreview.truncated ? <span>preview truncated</span> : null}
                </div>
                <pre>{artifactPreview.content}</pre>
              </div>
            ) : null}
          </section>
        ) : null}

        <ChatWindow
          ref={chatWindowRef}
          messages={messages}
          showTechnicalDetails={showTechnicalDetails}
          onPreviewArtifact={handlePreviewArtifact}
          onListArtifacts={handleListArtifacts}
          onCopyPath={handleCopyPath}
        />

        <InputBox
          disabled={loading || modelsLoading}
          loadingMessage={loading ? loadingStatusMessage : modelsLoading ? 'Loading available models...' : ''}
          statusMessage={
            !modelsLoading && !modelsLoadFailed && availableModels.length === 0
              ? activeProvider === 'ollama'
                ? 'No Ollama models are installed locally. Run ollama pull llama3:8b and refresh.'
                : 'No models are configured for the active provider.'
              : ''
          }
          models={availableModels}
          selectedModel={selectedModel}
          onModelChange={setSelectedModel}
          onSend={handleSend}
        />

        <footer className="app-footer">
          <span>Software Developer: Rod Oliveira</span>
          <a href="https://github.com/jrodolfo/local-genai-lab" target="_blank" rel="noreferrer">
            GitHub repo
          </a>
          <a href="https://jrodolfo.net" target="_blank" rel="noreferrer">
            Website
          </a>
          <span>© 2026 Rod Oliveira</span>
          <span>MIT License</span>
        </footer>
        </section>
      </section>
    </main>
  );
}

function mergeProviderMetadata(existingMetadata, updates) {
  const merged = {
    ...(existingMetadata || {}),
    ...(updates || {})
  };

  return Object.values(merged).some((value) => value != null) ? merged : null;
}

function formatElapsedTime(totalSeconds) {
  const seconds = Math.max(0, totalSeconds || 0);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;

  if (hours > 0) {
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`;
  }

  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`;
}

function formatProviderName(provider) {
  if (!provider) {
    return 'unknown';
  }
  if (provider.toLowerCase() === 'ollama') {
    return 'Ollama';
  }
  if (provider.toLowerCase() === 'bedrock') {
    return 'Bedrock';
  }
  return provider;
}

export default Home;
