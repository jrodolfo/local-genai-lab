import { useEffect, useRef, useState } from 'react';
import { listAvailableModels } from '../api/modelApi';
import { getRagStatus, queryRag, rebuildRagIndex } from '../api/ragApi';
import { retryAsync } from '../api/retry';
import { deleteSession, exportSession, getSession, importSession, listSessions } from '../api/sessionApi';
import RagAnswerWithSources from '../components/RagAnswerWithSources';
import './RagWorkspace.css';

function RagWorkspace() {
  const importInputRef = useRef(null);
  const [ragStatus, setRagStatus] = useState(null);
  const [availableProviders, setAvailableProviders] = useState([]);
  const [availableModels, setAvailableModels] = useState([]);
  const [selectedProvider, setSelectedProvider] = useState('');
  const [selectedModel, setSelectedModel] = useState('');
  const [sessionId, setSessionId] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [querying, setQuerying] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    loadWorkspace();
  }, []);

  useEffect(() => {
    if (!selectedProvider) {
      return;
    }
    loadModelsForProvider(selectedProvider);
  }, [selectedProvider]);

  async function loadWorkspace() {
    try {
      setLoading(true);
      setError('');
      const [statusPayload, modelsPayload, sessionsPayload] = await Promise.all([
        retryAsync(() => getRagStatus(), { retries: 8, delayMs: 500 }),
        retryAsync(() => listAvailableModels(), { retries: 8, delayMs: 500 }),
        retryAsync(() => listSessions({ mode: 'rag' }), { retries: 8, delayMs: 500 })
      ]);
      setRagStatus(statusPayload);
      hydrateProviders(modelsPayload);
      setSessions(sessionsPayload);
    } catch (err) {
      setError(err.message || 'Failed to load the RAG workspace.');
    } finally {
      setLoading(false);
    }
  }

  async function loadRagSessions() {
    const payload = await retryAsync(() => listSessions({ mode: 'rag' }), { retries: 4, delayMs: 500 });
    setSessions(payload);
  }

  async function loadModelsForProvider(provider) {
    try {
      const payload = await retryAsync(() => listAvailableModels(provider), { retries: 4, delayMs: 500 });
      setAvailableModels(Array.isArray(payload.models) ? payload.models : []);
      setSelectedModel((current) => {
        if (current && payload.models.includes(current)) {
          return current;
        }
        if (payload.defaultModel && payload.models.includes(payload.defaultModel)) {
          return payload.defaultModel;
        }
        return payload.models[0] || '';
      });
    } catch (err) {
      setError(err.message || 'Failed to load available models.');
    }
  }

  function hydrateProviders(payload) {
    const providers = Array.isArray(payload.providers) ? payload.providers : [];
    const models = Array.isArray(payload.models) ? payload.models : [];
    setAvailableProviders(providers);
    setAvailableModels(models);
    setSelectedProvider(payload.provider || payload.defaultProvider || providers[0] || '');
    setSelectedModel(payload.defaultModel || models[0] || '');
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (!question.trim() || !ragStatus?.enabled) {
      return;
    }
    try {
      setQuerying(true);
      setError('');
      const payload = await queryRag({
        question: question.trim(),
        provider: selectedProvider,
        model: selectedModel,
        sessionId
      });
      setSessionId(payload.sessionId);
      setQuestion('');
      await Promise.all([
        openSession(payload.sessionId),
        loadRagSessions()
      ]);
    } catch (err) {
      setError(err.message || 'Failed to query the RAG workspace.');
    } finally {
      setQuerying(false);
    }
  }

  async function handleRebuildIndex() {
    try {
      setRebuilding(true);
      setError('');
      const payload = await rebuildRagIndex();
      setRagStatus((current) => ({
        ...(current || {}),
        enabled: true,
        indexed: true,
        corpusRoot: payload.corpusRoot,
        documentCount: payload.documentCount,
        chunkCount: payload.chunkCount,
        retrievalMode: payload.retrievalMode
      }));
    } catch (err) {
      setError(err.message || 'Failed to rebuild the RAG index.');
    } finally {
      setRebuilding(false);
    }
  }

  async function openSession(targetSessionId) {
    try {
      setError('');
      const payload = await getSession(targetSessionId);
      setSessionId(payload.sessionId);
      setMessages(Array.isArray(payload.messages) ? payload.messages : []);
    } catch (err) {
      setError(err.message || 'Failed to load the RAG session.');
    }
  }

  function startNewSession() {
    setSessionId(null);
    setMessages([]);
    setQuestion('');
    setError('');
  }

  async function removeSession(targetSessionId) {
    try {
      await deleteSession(targetSessionId);
      if (sessionId === targetSessionId) {
        startNewSession();
      }
      await loadRagSessions();
    } catch (err) {
      setError(err.message || 'Failed to delete session.');
    }
  }

  async function downloadSession(targetSessionId, format) {
    try {
      const payload = await exportSession(targetSessionId, format);
      const url = window.URL.createObjectURL(payload.blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = payload.filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(err.message || 'Failed to export session.');
    }
  }

  async function handleImport(event) {
    const [file] = event.target.files || [];
    if (!file) {
      return;
    }
    try {
      const payload = await importSession(file);
      await Promise.all([
        loadRagSessions(),
        openSession(payload.sessionId)
      ]);
    } catch (err) {
      setError(err.message || 'Failed to import session.');
    } finally {
      event.target.value = '';
    }
  }

  return (
    <main className="rag-page">
      <section className="rag-hero">
        <div className="rag-hero__copy">
          <p className="rag-eyebrow">Experimental</p>
          <h1>RAG</h1>
          <p>Ask questions against the local docs corpus.</p>
        </div>
        <div className="rag-status-card">
          <div className="rag-status-card__header">
            <h2>Index</h2>
            {ragStatus?.enabled ? (
              <button type="button" onClick={handleRebuildIndex} disabled={rebuilding}>
                {rebuilding ? 'Rebuilding...' : 'Rebuild index'}
              </button>
            ) : null}
          </div>
          {loading ? <p>Loading RAG status...</p> : null}
          {!loading && ragStatus ? (
            <dl className="rag-status-grid">
              <div>
                <dt>Status</dt>
                <dd>{ragStatus.enabled ? (ragStatus.indexed ? 'ready' : 'not indexed') : 'disabled'}</dd>
              </div>
              <div>
                <dt>Corpus</dt>
                <dd>docs/</dd>
              </div>
              <div>
                <dt>Documents</dt>
                <dd>{ragStatus.documentCount}</dd>
              </div>
              <div>
                <dt>Chunks</dt>
                <dd>{ragStatus.chunkCount}</dd>
              </div>
              <div>
                <dt>Retrieval</dt>
                <dd>{ragStatus.retrievalMode}</dd>
              </div>
            </dl>
          ) : null}
        </div>
      </section>

      {error ? <p className="rag-error">{error}</p> : null}

      {!loading && ragStatus && !ragStatus.enabled ? (
        <section className="rag-empty-state">
          <h2>RAG is disabled</h2>
          <p>Enable `rag.enabled=true` in the backend to use this experimental workspace.</p>
        </section>
      ) : null}

      {!loading && ragStatus?.enabled ? (
        <section className="rag-layout">
          <aside className="rag-session-sidebar">
            <div className="rag-session-sidebar__header">
              <h2>RAG sessions</h2>
              <div className="rag-session-sidebar__actions">
                <button type="button" onClick={startNewSession}>New</button>
                <button type="button" onClick={() => importInputRef.current?.click()}>Import</button>
                <input
                  ref={importInputRef}
                  type="file"
                  accept="application/json"
                  className="rag-session-sidebar__import"
                  onChange={handleImport}
                />
              </div>
            </div>
            <div className="rag-session-list">
              {sessions.length === 0 ? (
                <p className="rag-session-empty">No saved RAG sessions yet.</p>
              ) : sessions.map((session) => (
                <article key={session.sessionId} className={`rag-session-item ${session.sessionId === sessionId ? 'active' : ''}`}>
                  <button type="button" className="rag-session-open" onClick={() => openSession(session.sessionId)}>
                    <span className="rag-session-title">{session.title}</span>
                    {session.summary ? <span className="rag-session-summary">{session.summary}</span> : null}
                    <span className="rag-session-meta">{new Date(session.updatedAt).toLocaleString()}</span>
                  </button>
                  <div className="rag-session-item__actions">
                    <button type="button" onClick={() => downloadSession(session.sessionId, 'json')}>JSON</button>
                    <button type="button" onClick={() => downloadSession(session.sessionId, 'markdown')}>MD</button>
                    <button type="button" onClick={() => removeSession(session.sessionId)}>Delete</button>
                  </div>
                </article>
              ))}
            </div>
          </aside>

          <section className="rag-workspace">
            <form className="rag-query-form" onSubmit={handleSubmit}>
              <div className="rag-field-grid">
                <label>
                  Provider
                  <select value={selectedProvider} onChange={(event) => setSelectedProvider(event.target.value)}>
                    {availableProviders.map((provider) => (
                      <option key={provider} value={provider}>
                        {provider}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Model
                  <select value={selectedModel} onChange={(event) => setSelectedModel(event.target.value)}>
                    {availableModels.map((model) => (
                      <option key={model} value={model}>
                        {model}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <label className="rag-question-field">
                Question
                <textarea
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                  placeholder="Ask a question about the project docs and ADRs."
                  rows={6}
                />
              </label>

              <div className="rag-actions">
                <button type="submit" disabled={querying || !question.trim()}>
                  {querying ? 'Querying...' : 'Ask docs corpus'}
                </button>
              </div>
            </form>

            {messages.length > 0 ? (
              <section className="rag-conversation" aria-label="RAG conversation history">
                {messages.map((message, index) => (
                  message.role === 'assistant' ? (
                    <RagAnswerWithSources
                      key={`${message.timestamp || index}-${index}`}
                      result={{
                        answer: message.content,
                        provider: message.metadata?.provider,
                        model: message.metadata?.modelId || selectedModel,
                        sources: message.ragSources || []
                      }}
                    />
                  ) : (
                    <section key={`${message.timestamp || index}-${index}`} className="rag-question-card">
                      <h2>Question</h2>
                      <p>{message.content}</p>
                    </section>
                  )
                ))}
              </section>
            ) : (
              <section className="rag-empty-state">
                <h2>No answer yet</h2>
                <p>Ask a question to retrieve the most relevant doc chunks and generate a cited answer.</p>
              </section>
            )}
          </section>
        </section>
      ) : null}
    </main>
  );
}

export default RagWorkspace;
