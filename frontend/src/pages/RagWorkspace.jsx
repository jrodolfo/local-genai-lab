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

const RAG_TECHNICAL_DETAILS_STORAGE_KEY = 'local-genai-lab-rag-technical-details';

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
    const [selectedRetrievalTarget, setSelectedRetrievalTarget] = useState('lexical:in-memory');
    const [sessionId, setSessionId] = useState(null);
    const [sessions, setSessions] = useState([]);
    const [showSessionsSidebar, setShowSessionsSidebar] = useState(true);
    const [showTechnicalDetails, setShowTechnicalDetails] = useState(() => {
        if (typeof window === 'undefined') {
            return false;
        }
        return window.localStorage.getItem(RAG_TECHNICAL_DETAILS_STORAGE_KEY) === 'true';
    });
    const [question, setQuestion] = useState('');
    const [messages, setMessages] = useState([]);
    const [loading, setLoading] = useState(true);
    const [querying, setQuerying] = useState(false);
    const [comparing, setComparing] = useState(false);
    const [comparisonResults, setComparisonResults] = useState([]);
    const [rebuilding, setRebuilding] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        loadWorkspace();
    }, []);

    useEffect(() => {
        window.localStorage.setItem(RAG_TECHNICAL_DETAILS_STORAGE_KEY, String(showTechnicalDetails));
    }, [showTechnicalDetails]);

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
            setSelectedRetrievalTarget(retrievalTargetFromStatus(statusPayload));
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
            const startedAt = Date.now();
            const payload = await queryRag({
                question: submittedQuestion,
                provider: selectedProvider,
                model: selectedModel,
                sessionId,
                ...retrievalOptionsFromTarget(selectedRetrievalTarget)
            });
            const elapsedMs = Date.now() - startedAt;
            setSessionId(payload.sessionId);
            setMessages((currentMessages) => [
                ...currentMessages,
                createRagQuestionMessage(submittedQuestion),
                createRagAnswerMessage(payload, selectedProvider, selectedModel, elapsedMs)
            ]);
            await loadRagSessions();
        } catch (err) {
            setError(err.message || 'Failed to query the RAG workspace.');
        } finally {
            setQuerying(false);
        }
    }

    async function handleCompareRetrievalTargets() {
        const submittedQuestion = question.trim();
        if (!submittedQuestion || !ragStatus?.enabled) {
            return;
        }

        const targets = retrievalTargets(ragStatus).filter((target) => target.available);
        try {
            setComparing(true);
            setError('');
            setComparisonResults([]);
            const results = [];
            for (const target of targets) {
                try {
                    const startedAt = Date.now();
                    const payload = await queryRag({
                        question: submittedQuestion,
                        provider: selectedProvider,
                        model: selectedModel,
                        persist: false,
                        ...retrievalOptionsFromTarget(target.value)
                    });
                    results.push({
                        status: 'success',
                        target,
                        payload: {
                            ...payload,
                            elapsedMs: Date.now() - startedAt
                        }
                    });
                } catch (err) {
                    results.push({
                        status: 'error',
                        target,
                        error: err.message || 'Failed to query this retrieval target.'
                    });
                }
            }
            setComparisonResults(results);
        } finally {
            setComparing(false);
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
            const payload = await rebuildRagIndex(retrievalOptionsFromTarget(selectedRetrievalTarget));
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
                            <h1>RAG</h1>
                            <p>Ask questions against the local docs corpus.</p>
                        </div>
                        <div className="rag-header-controls">
                            <label className="rag-debug-toggle">
                                <input
                                    type="checkbox"
                                    checked={showTechnicalDetails}
                                    onChange={(event) => setShowTechnicalDetails(event.target.checked)}
                                />
                                <span>show technical details</span>
                            </label>
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
                    </div>
                </div>
                <div className="rag-status-strip" aria-label="RAG index status">
                    {loading ? <span>Loading RAG status...</span> : null}
                    {!loading && ragStatus ? (
                        <div className="rag-status-strip__items">
                            <span><strong>Status</strong> {ragStatus.enabled ? (ragStatus.indexed ? 'ready' : 'not indexed') : 'disabled'}</span>
                            <span><strong>Corpus</strong> docs/</span>
                            <span><strong>Chunks</strong> {ragStatus.chunkCount}</span>
                            <span><strong>Selected</strong> {selectedRetrievalTargetLabel(selectedRetrievalTarget, ragStatus)}</span>
                        </div>
                    ) : null}
                    <div className="rag-status-strip__actions">
                        {ragStatus?.enabled ? (
                            <button type="button" className="rag-action-button rag-primary-button"
                                    onClick={handleRebuildIndex} disabled={rebuilding}>
                                {rebuilding ? 'Rebuilding...' : 'Rebuild Index'}
                            </button>
                        ) : null}
                    </div>
                </div>
                {!loading && ragStatus && showTechnicalDetails ? (
                    <div className="rag-status-card" aria-label="RAG technical status">
                    <div className="rag-status-card__header">
                        <h2>Index Details</h2>
                    </div>
                    <p className="rag-status-note">{retrievalModeHint(ragStatus)}</p>
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
                                <dt>Selected</dt>
                                <dd>{selectedRetrievalTargetLabel(selectedRetrievalTarget, ragStatus)}</dd>
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
                            {isQdrantRequired(ragStatus) ? (
                                <div>
                                    <dt>Qdrant</dt>
                                    <dd>{ragStatus.qdrantReachable ? 'Reachable' : 'Unavailable'}</dd>
                                </div>
                            ) : null}
                            {isQdrantRequired(ragStatus) ? (
                                <div>
                                    <dt>Collection</dt>
                                    <dd>{qdrantCollectionSummary(ragStatus)}</dd>
                                </div>
                            ) : null}
                        </dl>
                    {ragStatus.enabled && isQdrantRequired(ragStatus) ? (
                        <p className={`rag-status-note ${qdrantStatusTone(ragStatus)}`}>
                            {qdrantStatusMessage(ragStatus)}
                        </p>
                    ) : null}
                </div>
                ) : null}
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
                                    Retrieval
                                    <select value={selectedRetrievalTarget}
                                            onChange={(event) => setSelectedRetrievalTarget(event.target.value)}>
                                        {retrievalTargets(ragStatus).map((target) => (
                                            <option key={target.value} value={target.value} disabled={!target.available}>
                                                {target.label}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                            </div>

                            <p className="rag-selection-note">
                                {retrievalTargetHint(selectedRetrievalTarget, ragStatus)}
                                {' '}Rebuild Index applies to the selected retrieval target. If a vector target has not been indexed yet, rebuild before asking.
                            </p>

                            <div className="rag-field-grid rag-field-grid--single">
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
                                        disabled={querying || comparing || !question.trim()}>
                                    {querying ? 'Querying...' : 'Ask Docs Corpus'}
                                </button>
                                <button type="button" className="rag-action-button"
                                        disabled={querying || comparing || !question.trim()}
                                        onClick={handleCompareRetrievalTargets}>
                                    {comparing ? 'Comparing...' : 'Compare Retrieval Targets'}
                                </button>
                            </div>
                            <div className="rag-action-help" aria-live="polite">
                                {!question.trim() ? (
                                    <p>Enter a question to ask or compare retrieval targets.</p>
                                ) : null}
                                <p><strong>Ask Docs Corpus</strong> saves one answer using the selected retrieval target.</p>
                                <p><strong>Compare Retrieval Targets</strong> runs the same question across available targets without saving results.</p>
                            </div>
                        </form>

                        {comparisonResults.length > 0 ? (
                            <section className="rag-comparison" aria-label="RAG retrieval comparison">
                                <div className="rag-comparison__header">
                                    <h2>Retrieval Comparison</h2>
                                    <p>One question, compared across available retrieval targets. These results are not saved as conversation turns.</p>
                                </div>
                                <div className="rag-comparison__grid">
                                    {comparisonResults.map((result) => (
                                        <RagComparisonCard
                                            key={result.target.value}
                                            result={result}
                                            selectedProvider={selectedProvider}
                                            selectedModel={selectedModel}
                                            showTechnicalDetails={showTechnicalDetails}
                                        />
                                    ))}
                                </div>
                            </section>
                        ) : null}

                        {latestTurn ? (
                            <section className="rag-latest-turn" aria-label="Latest RAG turn">
                                <RagTurn
                                    turn={latestTurn}
                                    selectedModel={selectedModel}
                                    showTechnicalDetails={showTechnicalDetails}
                                />
                            </section>
                        ) : null}

                        {olderTurns.length > 0 ? (
                            <section className="rag-conversation" aria-label="RAG conversation history">
                                {olderTurns.map((turn, index) => (
                                    <RagTurn
                                        key={`${turn.question?.timestamp || turn.answer?.timestamp || index}-${index}`}
                                        turn={turn}
                                        selectedModel={selectedModel}
                                        showTechnicalDetails={showTechnicalDetails}
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

function RagTurn({turn, selectedModel, showTechnicalDetails}) {
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
                        elapsedMs: turn.answer.metadata?.elapsedMs,
                        ragRetrieval: turn.answer.ragRetrieval,
                        ragTiming: turn.answer.ragTiming,
                        sources: turn.answer.ragSources || []
                    }}
                    showTechnicalDetails={showTechnicalDetails}
                />
            ) : null}
        </>
    );
}

function RagComparisonCard({result, selectedProvider, selectedModel, showTechnicalDetails}) {
    if (result.status === 'error') {
        return (
            <article className="rag-comparison-card rag-comparison-card--error">
                <h3>{result.target.label}</h3>
                <p>{result.error}</p>
            </article>
        );
    }

    return (
        <article className="rag-comparison-card">
            <h3>{result.target.label}</h3>
            <RagAnswerWithSources
                result={{
                    answer: result.payload.answer,
                    provider: result.payload.metadata?.provider || result.payload.provider || selectedProvider,
                    model: result.payload.metadata?.modelId || result.payload.model || selectedModel,
                    elapsedMs: result.payload.elapsedMs,
                    ragRetrieval: result.payload.ragRetrieval || retrievalMetadataFromTarget(result.target.value),
                    ragTiming: result.payload.ragTiming,
                    sources: result.payload.sources || []
                }}
                showTechnicalDetails={showTechnicalDetails}
            />
        </article>
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

function isQdrantRequired(status) {
    return Boolean(status?.qdrantRequired);
}

function formatEmbeddingStatus(status) {
    const provider = formatRagStatusValue(status?.embeddingProvider || 'ollama');
    const model = status?.embeddingModel || 'nomic-embed-text';
    return `${provider} / ${model}`;
}

function retrievalModeHint(status) {
    if (isVectorRetrieval(status)) {
        return 'Backend default is vector retrieval. Use the Retrieval selector to override per question.';
    }
    return 'Backend default is lexical retrieval. Use the Retrieval selector to try vector retrieval per question.';
}

function qdrantStatusMessage(status) {
    if (status?.qdrantReachable && status.qdrantCollectionExists === false) {
        return status.qdrantStatusMessage || `Qdrant collection ${status.qdrantCollection || 'the configured collection'} is missing. Rebuild the index.`;
    }
    if (status?.qdrantReachable) {
        return status.qdrantStatusMessage || qdrantCollectionSummary(status);
    }
    return status?.qdrantStatusMessage
        ? `${status.qdrantStatusMessage} Start it and rebuild the index.`
        : `Qdrant is not reachable at ${status?.qdrantUrl || 'the configured URL'}. Start it and rebuild the index.`;
}

function qdrantStatusTone(status) {
    return status?.qdrantReachable && status?.qdrantCollectionExists !== false
        ? 'rag-status-note-ok'
        : 'rag-status-note-warning';
}

function qdrantCollectionSummary(status) {
    if (!status?.qdrantReachable) {
        return 'Not checked';
    }
    if (status.qdrantCollectionExists === false) {
        return 'Missing';
    }
    if (status.qdrantCollectionExists) {
        return typeof status.qdrantPointCount === 'number'
            ? `Present, ${status.qdrantPointCount} points`
            : 'Present, points unknown';
    }
    return 'Unknown';
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

function createRagAnswerMessage(payload, selectedProvider, selectedModel, elapsedMs) {
    return {
        role: 'assistant',
        content: payload.answer,
        metadata: {
            provider: payload.metadata?.provider || payload.provider || selectedProvider,
            modelId: payload.metadata?.modelId || payload.model || selectedModel,
            elapsedMs
        },
        ragSources: payload.sources || [],
        ragRetrieval: payload.ragRetrieval || null,
        ragTiming: payload.ragTiming || null,
        timestamp: new Date().toISOString()
    };
}

function retrievalTargetFromStatus(status) {
    const mode = String(status?.retrievalMode || 'lexical').toLowerCase();
    const store = String(status?.vectorStore || 'in-memory').toLowerCase();
    if (mode === 'vector' && store === 'qdrant') {
        return 'vector:qdrant';
    }
    if (mode === 'vector') {
        return 'vector:in-memory';
    }
    return 'lexical:in-memory';
}

function retrievalOptionsFromTarget(target) {
    const [retrievalMode, vectorStore] = String(target || 'lexical:in-memory').split(':');
    return {
        retrievalMode: retrievalMode || 'lexical',
        vectorStore: vectorStore || 'in-memory'
    };
}

function retrievalMetadataFromTarget(target) {
    const options = retrievalOptionsFromTarget(target);
    return {
        ...options,
        retrievalTarget: `${options.retrievalMode}:${options.vectorStore}`
    };
}

function retrievalTargets(status) {
    if (Array.isArray(status?.retrievalTargets) && status.retrievalTargets.length > 0) {
        return status.retrievalTargets.map((target) => ({
            ...target,
            available: target.available !== false
        }));
    }

    const qdrantReady = status?.qdrantReachable !== false && status?.qdrantCollectionExists !== false;
    return [
        {
            value: 'lexical:in-memory',
            label: 'Lexical',
            available: true,
            message: 'Uses the zero-dependency lexical index for this request.'
        },
        {
            value: 'vector:in-memory',
            label: 'Vector - In Memory',
            available: true,
            message: 'Uses Ollama embeddings and an in-memory vector index for this request.'
        },
        {
            value: 'vector:qdrant',
            label: qdrantReady ? 'Vector - Qdrant' : 'Vector - Qdrant Unavailable',
            available: qdrantReady,
            message: qdrantReady
                ? 'Uses Ollama embeddings and Qdrant for vector search. Rebuild the index after switching to this target.'
                : 'Qdrant is unavailable. Start Qdrant, rebuild the index, or choose another retrieval mode.'
        }
    ];
}

function retrievalTargetHint(target, status) {
    const selectedTarget = retrievalTargets(status).find((candidate) => candidate.value === target);
    if (selectedTarget?.message) {
        return selectedTarget.message;
    }
    if (target === 'vector:in-memory') {
        return 'Uses Ollama embeddings and an in-memory vector index for this request.';
    }
    return 'Uses the zero-dependency lexical index for this request.';
}

function selectedRetrievalTargetLabel(target, status) {
    return retrievalTargets(status).find((candidate) => candidate.value === target)?.label || formatRagStatusValue(target);
}

export default RagWorkspace;
