/**
 * @fileoverview RagWorkspace page component for experimental RAG (Retrieval-Augmented Generation) mode.
 * Allows users to query a local document corpus and view cited answers.
 */
import {useEffect, useRef, useState} from 'react';
import {listAvailableModels} from '../api/modelApi';
import {getRagStatus, queryRag, rebuildRagIndex} from '../api/ragApi';
import {retryAsync} from '../api/retry';
import {deleteSession, exportSession, getSession, importSession, listSessions} from '../api/sessionApi';
import RagAnswerWithSources from '../components/RagAnswerWithSources';
import './RagWorkspace.css';

/**
 * RagWorkspace component.
 *
 * @returns {React.JSX.Element} The rendered RagWorkspace page.
 */
function RagWorkspace() {
    const importInputRef = useRef(null);
    const [ragStatus, setRagStatus] = useState(null);
    const [availableProviders, setAvailableProviders] = useState([]);
    const [availableModels, setAvailableModels] = useState([]);
    const [selectedProvider, setSelectedProvider] = useState('');
    const [selectedModel, setSelectedModel] = useState('');
    const [sessionId, setSessionId] = useState(null);
    const [sessions, setSessions] = useState([]);
    const [showSessionsSidebar, setShowSessionsSidebar] = useState(true);
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

    /**
     * Loads the initial workspace state, including RAG status, available models, and sessions.
     *
     * @returns {Promise<void>}
     */
    async function loadWorkspace() {
        try {
            setLoading(true);
            setError('');
            const [statusPayload, modelsPayload, sessionsPayload] = await Promise.all([
                retryAsync(() => getRagStatus(), {retries: 8, delayMs: 500}),
                retryAsync(() => listAvailableModels(), {retries: 8, delayMs: 500}),
                retryAsync(() => listSessions({mode: 'rag'}), {retries: 8, delayMs: 500})
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
        const payload = await retryAsync(() => listSessions({mode: 'rag'}), {retries: 4, delayMs: 500});
        setSessions(payload);
    }

    async function loadModelsForProvider(provider) {
        try {
            const payload = await retryAsync(() => listAvailableModels(provider), {retries: 4, delayMs: 500});
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
        const submittedQuestion = question.trim();
        if (!submittedQuestion || !ragStatus?.enabled) {
            return;
        }
        try {
            setQuerying(true);
            setError('');
            const payload = await queryRag({
                question: submittedQuestion,
                provider: selectedProvider,
                model: selectedModel,
                sessionId
            });
            setSessionId(payload.sessionId);
            setQuestion('');
            setMessages((currentMessages) => [
                ...currentMessages,
                createRagQuestionMessage(submittedQuestion),
                createRagAnswerMessage(payload, selectedProvider, selectedModel)
            ]);
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

    /**
     * Rebuilds the RAG index in the backend.
     *
     * @returns {Promise<void>}
     */
    async function handleRebuildIndex() {
        try {
            setRebuilding(true);
            setError('');
            const payload = await rebuildRagIndex();
            const statusPayload = await getRagStatus();
            setRagStatus({
                ...statusPayload,
                enabled: true,
                indexed: true,
                corpusRoot: payload.corpusRoot,
                documentCount: payload.documentCount,
                chunkCount: payload.chunkCount,
                retrievalMode: payload.retrievalMode
            });
        } catch (err) {
            setError(err.message || 'Failed to rebuild the RAG index.');
        } finally {
            setRebuilding(false);
        }
    }

    /**
     * Opens a specific RAG session and loads its messages.
     *
     * @param {string} targetSessionId - The session ID to open.
     * @returns {Promise<void>}
     */
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

    const ragTurns = buildRagTurns(messages);
    const latestTurn = findLatestAnsweredTurn(ragTurns);
    const olderTurns = latestTurn
        ? ragTurns.filter((turn) => turn !== latestTurn).reverse()
        : ragTurns.slice().reverse();

    return (
        <main className="rag-page">
            <section className="rag-hero">
                <div className="rag-hero__copy">
                    <div className="rag-hero__header">
                        <div>
                            <p className="rag-eyebrow">Experimental</p>
                            <h1>RAG</h1>
                        </div>
                        <button
                            type="button"
                            className="rag-action-button rag-sidebar-toggle"
                            aria-expanded={showSessionsSidebar}
                            aria-controls="rag-sessions-sidebar"
                            onClick={() => setShowSessionsSidebar((current) => !current)}
                        >
                            {showSessionsSidebar ? 'Hide Sessions' : 'Show Sessions'}
                        </button>
                    </div>
                    <p>Ask questions against the local docs corpus.</p>
                </div>
                <div className="rag-status-card">
                    <div className="rag-status-card__header">
                        <h2>Index</h2>
                        {ragStatus?.enabled ? (
                            <button type="button" className="rag-action-button rag-primary-button"
                                    onClick={handleRebuildIndex} disabled={rebuilding}>
                                {rebuilding ? 'Rebuilding...' : 'Rebuild Index'}
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
                                <dd>{formatRagStatusValue(ragStatus.retrievalMode)}</dd>
                            </div>
                            <div>
                                <dt>Store</dt>
                                <dd>{formatRagStatusValue(ragStatus.retrievalStore || 'in-memory')}</dd>
                            </div>
                            {isVectorRetrieval(ragStatus) ? (
                                <div>
                                    <dt>Embedding</dt>
                                    <dd>{formatEmbeddingStatus(ragStatus)}</dd>
                                </div>
                            ) : null}
                        </dl>
                    ) : null}
                    {!loading && ragStatus?.enabled ? (
                        <p className="rag-status-note">{retrievalModeHint(ragStatus)}</p>
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
                <section className={`rag-layout ${showSessionsSidebar ? '' : 'sidebar-hidden'}`.trim()}>
                    {showSessionsSidebar ? (
                        <aside id="rag-sessions-sidebar" className="rag-session-sidebar">
                            <div className="rag-session-sidebar__header">
                                <h2>RAG sessions</h2>
                                <div className="rag-session-sidebar__actions">
                                    <button type="button" className="rag-action-button" onClick={startNewSession}>New
                                        Session
                                    </button>
                                    <button type="button" className="rag-action-button"
                                            onClick={() => importInputRef.current?.click()}>Import Session
                                    </button>
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
                                    <article key={session.sessionId}
                                             className={`rag-session-item ${session.sessionId === sessionId ? 'active' : ''}`}>
                                        <button type="button" className="rag-session-open"
                                                onClick={() => openSession(session.sessionId)}>
                                            <span className="rag-session-title">{session.title}</span>
                                            {session.summary ?
                                                <span className="rag-session-summary">{session.summary}</span> : null}
                                            <span
                                                className="rag-session-meta">{new Date(session.updatedAt).toLocaleString()}</span>
                                        </button>
                                        <div className="rag-session-item__actions">
                                            <button type="button" className="rag-action-button"
                                                    onClick={() => downloadSession(session.sessionId, 'json')}>Export
                                                JSON
                                            </button>
                                            <button type="button" className="rag-action-button"
                                                    onClick={() => downloadSession(session.sessionId, 'markdown')}>Export
                                                Markdown
                                            </button>
                                            <button type="button" className="rag-action-button rag-action-button-danger"
                                                    onClick={() => removeSession(session.sessionId)}>Delete
                                            </button>
                                        </div>
                                    </article>
                                ))}
                            </div>
                        </aside>
                    ) : null}

                    <section className="rag-workspace">
                        <form className="rag-query-form" onSubmit={handleSubmit}>
                            <div className="rag-field-grid">
                                <label>
                                    Provider
                                    <select value={selectedProvider}
                                            onChange={(event) => setSelectedProvider(event.target.value)}>
                                        {availableProviders.map((provider) => (
                                            <option key={provider} value={provider}>
                                                {provider}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <label>
                                    Model
                                    <select value={selectedModel}
                                            onChange={(event) => setSelectedModel(event.target.value)}>
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
                                <button type="submit" className="rag-action-button rag-primary-button"
                                        disabled={querying || !question.trim()}>
                                    {querying ? 'Querying...' : 'Ask Docs Corpus'}
                                </button>
                            </div>
                        </form>

                        {latestTurn ? (
                            <section className="rag-latest-turn" aria-label="Latest RAG turn">
                                <RagTurn turn={latestTurn} selectedModel={selectedModel}/>
                            </section>
                        ) : null}

                        {olderTurns.length > 0 ? (
                            <section className="rag-conversation" aria-label="RAG conversation history">
                                {olderTurns.map((turn, index) => (
                                    <RagTurn
                                        key={`${turn.question?.timestamp || turn.answer?.timestamp || index}-${index}`}
                                        turn={turn}
                                        selectedModel={selectedModel}
                                    />
                                ))}
                            </section>
                        ) : null}

                        {messages.length === 0 ? (
                            <section className="rag-empty-state">
                                <h2>No answer yet</h2>
                                <p>Ask a question to retrieve the most relevant doc chunks and generate a cited
                                    answer.</p>
                            </section>
                        ) : null}
                    </section>
                </section>
            ) : null}
        </main>
    );
}

function RagTurn({turn, selectedModel}) {
    return (
        <>
            {turn.question ? (
                <section className="rag-question-card">
                    <h2>Question</h2>
                    <p>{turn.question.content}</p>
                </section>
            ) : null}
            {turn.answer ? (
                <RagAnswerWithSources
                    result={{
                        answer: turn.answer.content,
                        provider: turn.answer.metadata?.provider,
                        model: turn.answer.metadata?.modelId || selectedModel,
                        sources: turn.answer.ragSources || []
                    }}
                />
            ) : null}
        </>
    );
}

function formatRagStatusValue(value) {
    const normalized = String(value || '').replaceAll('-', ' ');
    if (!normalized) {
        return '';
    }
    return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function isVectorRetrieval(status) {
    return String(status?.retrievalMode || '').toLowerCase() === 'vector';
}

function formatEmbeddingStatus(status) {
    const provider = formatRagStatusValue(status?.embeddingProvider || 'ollama');
    const model = status?.embeddingModel || 'nomic-embed-text';
    return `${provider} / ${model}`;
}

function retrievalModeHint(status) {
    if (isVectorRetrieval(status)) {
        return 'Experimental local vector retrieval. Change RAG_RETRIEVAL_MODE and restart to switch modes.';
    }
    return 'Default zero-dependency lexical baseline. Change RAG_RETRIEVAL_MODE=vector and restart to try vector retrieval.';
}

function buildRagTurns(messages) {
    const turns = [];
    let pendingQuestion = null;

    for (const message of messages) {
        if (message.role === 'user') {
            if (pendingQuestion) {
                turns.push({question: pendingQuestion, answer: null});
            }
            pendingQuestion = message;
            continue;
        }

        if (message.role === 'assistant') {
            turns.push({question: pendingQuestion, answer: message});
            pendingQuestion = null;
        }
    }

    if (pendingQuestion) {
        turns.push({question: pendingQuestion, answer: null});
    }

    return turns;
}

function findLatestAnsweredTurn(turns) {
    for (let index = turns.length - 1; index >= 0; index -= 1) {
        if (turns[index].answer) {
            return turns[index];
        }
    }
    return null;
}

function createRagQuestionMessage(content) {
    return {
        role: 'user',
        content,
        metadata: null,
        ragSources: null,
        timestamp: new Date().toISOString()
    };
}

function createRagAnswerMessage(payload, selectedProvider, selectedModel) {
    return {
        role: 'assistant',
        content: payload.answer,
        metadata: {
            provider: payload.metadata?.provider || payload.provider || selectedProvider,
            modelId: payload.metadata?.modelId || payload.model || selectedModel
        },
        ragSources: payload.sources || [],
        timestamp: new Date().toISOString()
    };
}

export default RagWorkspace;
