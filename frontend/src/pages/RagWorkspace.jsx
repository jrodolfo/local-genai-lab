/**
 * @fileoverview RagWorkspace page component for experimental RAG (Retrieval-Augmented Generation) mode.
 * Allows users to query a local document corpus and view cited answers.
 */
import {useEffect, useRef, useState} from 'react';
import {listAvailableModels} from '../api/modelApi';
import {compareRagRetrievalTargets, getRagStatus, queryRag, rebuildRagIndex} from '../api/ragApi';
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
    const [selectedRetrievalTarget, setSelectedRetrievalTarget] = useState('lexical');
    const [sessionId, setSessionId] = useState(null);
    const [sessions, setSessions] = useState([]);
    const [showSessionsSidebar, setShowSessionsSidebar] = useState(true);
    const [showTechnicalDetails, setShowTechnicalDetails] = useState(false);
    const [question, setQuestion] = useState('');
    const [messages, setMessages] = useState([]);
    const [loading, setLoading] = useState(true);
    const [querying, setQuerying] = useState(false);
    const [comparing, setComparing] = useState(false);
    const [comparisonResult, setComparisonResult] = useState(null);
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
            setSelectedRetrievalTarget(defaultRetrievalTarget(statusPayload));
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

    async function refreshRagStatus() {
        const statusPayload = await getRagStatus();
        setRagStatus(statusPayload);
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

    /**
     * Hydrates provider and model selector state from the backend model payload.
     *
     * @param {Object} payload backend response from the model discovery endpoint
     * @param {string[]} [payload.providers] provider ids configured in the backend
     * @param {string[]} [payload.models] models available for the selected provider
     * @param {string} [payload.provider] active provider selected by the backend
     * @param {string} [payload.defaultProvider] fallback provider when no active provider is present
     * @param {string} [payload.defaultModel] preferred model for the selected provider
     */
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
            setComparisonResult(null);
            const payload = await queryRag({
                question: submittedQuestion,
                provider: selectedProvider,
                model: selectedModel,
                sessionId,
                retrievalTarget: selectedRetrievalTarget
            });
            setSessionId(payload.sessionId);
            setMessages((currentMessages) => [
                ...currentMessages,
                createRagQuestionMessage(submittedQuestion),
                createRagAnswerMessage(payload, selectedProvider, selectedModel)
            ]);
            setQuestion('');
            await Promise.all([
                openSession(payload.sessionId),
                loadRagSessions(),
                refreshRagStatus().catch(() => null)
            ]);
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
        try {
            setComparing(true);
            setError('');
            const payload = await compareRagRetrievalTargets({
                question: submittedQuestion,
                provider: selectedProvider,
                model: selectedModel
            });
            setComparisonResult(payload);
            setQuestion('');
            await refreshRagStatus().catch(() => null);
        } catch (err) {
            setError(err.message || 'Failed to compare RAG retrieval targets.');
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
        setComparisonResult(null);
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
            <section className={`rag-shell ${showSessionsSidebar ? '' : 'sidebar-hidden'}`.trim()}>
                {showSessionsSidebar ? (
                    <RagSessionSidebar
                        importInputRef={importInputRef}
                        sessions={sessions}
                        sessionId={sessionId}
                        onStartNewSession={startNewSession}
                        onImportClick={() => importInputRef.current?.click()}
                        onImport={handleImport}
                        onOpenSession={openSession}
                        onDownloadSession={downloadSession}
                        onRemoveSession={removeSession}
                    />
                ) : null}

                <section className="rag-main-card">
                    <header className="rag-main-header">
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
                                className="rag-action-button"
                                aria-expanded={showSessionsSidebar}
                                aria-controls="rag-sessions-sidebar"
                                onClick={() => setShowSessionsSidebar((current) => !current)}
                            >
                                {showSessionsSidebar ? 'Hide Sessions' : 'Show Sessions'}
                            </button>
                        </div>
                    </header>

                    <RagStatusStrip
                        loading={loading}
                        ragStatus={ragStatus}
                        rebuilding={rebuilding}
                        onRebuildIndex={handleRebuildIndex}
                    />

                    {error ? <p className="rag-error">{error}</p> : null}

                    {!loading && ragStatus && !ragStatus.enabled ? (
                        <section className="rag-empty-state">
                            <h2>RAG is disabled</h2>
                            <p>Enable `rag.enabled=true` in the backend to use this workspace.</p>
                        </section>
                    ) : null}

                    {!loading && ragStatus?.enabled ? (
                        <>
                            <RagQueryCard
                                availableProviders={availableProviders}
                                availableModels={availableModels}
                                selectedProvider={selectedProvider}
                                selectedModel={selectedModel}
                                selectedRetrievalTarget={selectedRetrievalTarget}
                                ragStatus={ragStatus}
                                question={question}
                                querying={querying}
                                comparing={comparing}
                                onProviderChange={setSelectedProvider}
                                onModelChange={setSelectedModel}
                                onRetrievalTargetChange={setSelectedRetrievalTarget}
                                onQuestionChange={setQuestion}
                                onSubmit={handleSubmit}
                                onCompare={handleCompareRetrievalTargets}
                            />

                            {comparisonResult ? (
                                <RagComparisonResults
                                    comparison={comparisonResult}
                                    selectedModel={selectedModel}
                                    showTechnicalDetails={showTechnicalDetails}
                                />
                            ) : null}

                            {latestTurn ? (
                                <section className="rag-latest-answer" aria-label="Latest RAG answer">
                                    <h2>Latest answer</h2>
                                    <RagAnswerTurn
                                        turn={latestTurn}
                                        selectedModel={selectedModel}
                                        showTechnicalDetails={showTechnicalDetails}
                                        ariaLabel="Latest RAG turn"
                                    />
                                </section>
                            ) : (
                                <section className="rag-empty-state rag-empty-state--inline">
                                    <h2>No answer yet</h2>
                                    <p>Ask a question to retrieve cited chunks from the docs corpus.</p>
                                </section>
                            )}

                            {olderTurns.length > 0 ? (
                                <section className="rag-history" aria-label="RAG conversation history">
                                    <h2>Previous answers</h2>
                                    {olderTurns.map((turn, index) => (
                                        <RagAnswerTurn
                                            key={`${turn.question?.timestamp || turn.answer?.timestamp || index}-${index}`}
                                            turn={turn}
                                            selectedModel={selectedModel}
                                            showTechnicalDetails={showTechnicalDetails}
                                            ariaLabel="RAG history turn"
                                        />
                                    ))}
                                </section>
                            ) : null}
                        </>
                    ) : null}
                </section>
            </section>
        </main>
    );
}

function RagSessionSidebar({
                               importInputRef,
                               sessions,
                               sessionId,
                               onStartNewSession,
                               onImportClick,
                               onImport,
                               onOpenSession,
                               onDownloadSession,
                               onRemoveSession
                           }) {
    return (
        <aside id="rag-sessions-sidebar" className="rag-session-sidebar">
            <div className="rag-session-sidebar__header">
                <h2>Sessions</h2>
                <div className="rag-session-sidebar__actions">
                    <button type="button" className="rag-action-button" onClick={onStartNewSession}>
                        New Session
                    </button>
                    <button type="button" className="rag-action-button" onClick={onImportClick}>
                        Import Session
                    </button>
                    <input
                        ref={importInputRef}
                        type="file"
                        accept="application/json"
                        className="rag-session-sidebar__import"
                        onChange={onImport}
                    />
                </div>
                <p className="rag-session-export-help">
                    Export Markdown for reading. Export JSON for import or backup.
                </p>
            </div>
            <div className="rag-session-list">
                {sessions.length === 0 ? (
                    <p className="rag-session-empty">No saved RAG sessions yet.</p>
                ) : sessions.map((session) => (
                    <article
                        key={session.sessionId}
                        className={`rag-session-item ${session.sessionId === sessionId ? 'active' : ''}`}
                    >
                        <button type="button" className="rag-session-open"
                                onClick={() => onOpenSession(session.sessionId)}>
                            <span className="rag-session-title">{session.title}</span>
                            {session.summary ? <span className="rag-session-summary">{session.summary}</span> : null}
                            <span className="rag-session-meta">{new Date(session.updatedAt).toLocaleString()}</span>
                        </button>
                        <div className="rag-session-item__actions">
                            <button type="button" className="rag-action-button"
                                    onClick={() => onDownloadSession(session.sessionId, 'json')}>
                                Export JSON
                            </button>
                            <button type="button" className="rag-action-button"
                                    onClick={() => onDownloadSession(session.sessionId, 'markdown')}>
                                Export Markdown
                            </button>
                            <button type="button" className="rag-action-button rag-action-button-danger"
                                    onClick={() => onRemoveSession(session.sessionId)}>
                                Delete
                            </button>
                        </div>
                    </article>
                ))}
            </div>
        </aside>
    );
}

function RagStatusStrip({loading, ragStatus, rebuilding, onRebuildIndex}) {
    return (
        <section className="rag-status-strip" aria-label="RAG index status">
            <div className="rag-status-strip__summary">
                <h2>Index</h2>
                {loading ? <p>Loading RAG status...</p> : null}
                {!loading && ragStatus ? (
                    <dl>
                        <div>
                            <dt>Status</dt>
                            <dd>{formatIndexStatus(ragStatus)}</dd>
                        </div>
                        <div>
                            <dt>Corpus</dt>
                            <dd>docs/</dd>
                        </div>
                        <div>
                            <dt>Documents</dt>
                            <dd>{formatIndexCount(ragStatus, ragStatus.documentCount)}</dd>
                        </div>
                        <div>
                            <dt>Chunks</dt>
                            <dd>{formatIndexCount(ragStatus, ragStatus.chunkCount)}</dd>
                        </div>
                        <div>
                            <dt>Default Retrieval</dt>
                            <dd>{formatRagStatusValue(ragStatus.retrievalMode)}</dd>
                        </div>
                        <div>
                            <dt>Default Store</dt>
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
                ) : null}
                {ragStatus?.enabled ? (
                    <button type="button" className="rag-action-button rag-primary-button"
                            onClick={onRebuildIndex} disabled={rebuilding}>
                        {rebuilding ? 'Rebuilding...' : 'Rebuild Index'}
                    </button>
                ) : null}
            </div>
            {!loading && ragStatus?.enabled ? (
                <p className="rag-status-note">{retrievalModeHint(ragStatus)}</p>
            ) : null}
            {!loading && ragStatus?.enabled ? (
                <p className="rag-status-note">
                    Rebuild Index is optional. Use it after changing docs, switching retrieval settings, or troubleshooting stale results.
                </p>
            ) : null}
            {!loading && ragStatus?.enabled && isQdrantRequired(ragStatus) ? (
                <p className={`rag-status-note ${qdrantStatusTone(ragStatus)}`}>
                    {qdrantStatusMessage(ragStatus)}
                </p>
            ) : null}
        </section>
    );
}

function RagQueryCard({
                          availableProviders,
                          availableModels,
                          selectedProvider,
                          selectedModel,
                          selectedRetrievalTarget,
                          ragStatus,
                          question,
                          querying,
                          comparing,
                          onProviderChange,
                          onModelChange,
                          onRetrievalTargetChange,
                          onQuestionChange,
                          onSubmit,
                          onCompare
                      }) {
    return (
        <form className="rag-query-card" aria-label="RAG query" onSubmit={onSubmit}>
            <div className="rag-query-card__controls">
                <label>
                    Provider
                    <select value={selectedProvider}
                            onChange={(event) => onProviderChange(event.target.value)}>
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
                            onChange={(event) => onModelChange(event.target.value)}>
                        {availableModels.map((model) => (
                            <option key={model} value={model}>
                                {model}
                            </option>
                        ))}
                    </select>
                </label>
                <label>
                    Retrieval
                    <select value={selectedRetrievalTarget}
                            onChange={(event) => onRetrievalTargetChange(event.target.value)}>
                        {retrievalTargetOptions(ragStatus).map((option) => (
                            <option key={option.value} value={option.value} disabled={option.disabled}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                </label>
            </div>

            <label className="rag-query-card__question">
                Question
                <textarea
                    value={question}
                    onChange={(event) => onQuestionChange(event.target.value)}
                    placeholder="Ask a question about the project docs and ADRs."
                    rows={3}
                />
            </label>

            <div className="rag-query-card__actions">
                <button type="submit" className="rag-action-button rag-primary-button"
                        disabled={querying || comparing || !question.trim()}>
                    {querying ? 'Querying...' : 'Ask Docs Corpus'}
                </button>
                <button type="button" className="rag-action-button"
                        disabled={querying || comparing || !question.trim()}
                        onClick={onCompare}>
                    {comparing ? 'Comparing...' : 'Compare Retrieval Targets'}
                </button>
            </div>
            <p className="rag-query-card__help">
                Compare Retrieval Targets runs the same question against Lexical, Vector - In Memory, and Vector - Qdrant without saving results.
            </p>
            <QdrantReadinessHint ragStatus={ragStatus}/>
        </form>
    );
}

function QdrantReadinessHint({ragStatus}) {
    const readiness = qdrantReadiness(ragStatus);
    if (!readiness) {
        return null;
    }

    return (
        <p className={`rag-query-card__readiness ${readiness.className}`} aria-label="Qdrant readiness">
            <strong>{readiness.label}</strong>
            {' '}
            {readiness.detail}
        </p>
    );
}

function RagComparisonResults({comparison, selectedModel, showTechnicalDetails}) {
    const results = Array.isArray(comparison?.results) ? comparison.results : [];
    if (results.length === 0) {
        return null;
    }

    return (
        <section className="rag-comparison-results" aria-label="RAG retrieval comparison">
            <div className="rag-comparison-results__header">
                <h2>Retrieval comparison</h2>
                <p>Diagnostic results only. These answers are not saved to RAG sessions.</p>
            </div>
            <RagQuestionSummary question={comparison.question}/>
            <div className="rag-comparison-results__list">
                {results.map((result) => (
                    <article key={result.retrievalTarget} className="rag-comparison-target">
                        <header className="rag-comparison-target__header">
                            <h3>{formatRetrievalTargetLabel(result.retrievalTarget)}</h3>
                            <span className={result.success ? 'rag-comparison-target__status-ok' : 'rag-comparison-target__status-error'}>
                                {comparisonStatusLabel(result)}
                            </span>
                        </header>
                        {result.success ? (
                            <RagAnswerWithSources
                                result={{
                                    answer: result.answer,
                                    provider: result.provider,
                                    model: result.model || selectedModel,
                                    sources: result.sources || [],
                                    ragRetrieval: result.ragRetrieval || null,
                                    ragTiming: result.ragTiming || null
                                }}
                                showTechnicalDetails={showTechnicalDetails}
                            />
                        ) : (
                            <p className="rag-comparison-target__error">{result.error || 'This retrieval target failed.'}</p>
                        )}
                    </article>
                ))}
            </div>
        </section>
    );
}

function RagAnswerTurn({turn, selectedModel, showTechnicalDetails, ariaLabel}) {
    return (
        <section className="rag-answer-turn" aria-label={ariaLabel}>
            <RagQuestionSummary question={turn.question?.content}/>
            {turn.answer ? (
                <RagAnswerWithSources
                    result={{
                        answer: turn.answer.content,
                        provider: turn.answer.metadata?.provider,
                        model: turn.answer.metadata?.modelId || selectedModel,
                        sources: turn.answer.ragSources || [],
                        ragRetrieval: turn.answer.ragRetrieval || null,
                        ragTiming: turn.answer.ragTiming || null
                    }}
                    showTechnicalDetails={showTechnicalDetails}
                />
            ) : null}
        </section>
    );
}

function RagQuestionSummary({question}) {
    if (!question) {
        return null;
    }

    return (
        <div className="rag-question-summary">
            <span>Question</span>
            <p>{question}</p>
        </div>
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

/**
 * Resolves the initial selector value from backend startup defaults.
 *
 * @param {Object|null} status - RAG status payload from the backend.
 * @returns {string} One of the API retrieval target values.
 */
function defaultRetrievalTarget(status) {
    if (String(status?.retrievalMode || '').toLowerCase() !== 'vector') {
        return 'lexical';
    }
    return String(status?.vectorStore || '').toLowerCase() === 'qdrant' ? 'vector:qdrant' : 'vector:in-memory';
}

/**
 * Builds the RAG retrieval selector options shown in the query form.
 *
 * @param {Object|null} status - RAG status payload used to disable unavailable targets.
 * @returns {Array<{value: string, label: string, disabled?: boolean}>} Selectable retrieval targets.
 */
function retrievalTargetOptions(status) {
    const qdrantUnavailable = isQdrantRequired(status) && status?.qdrantReachable === false;
    return [
        {value: 'lexical', label: 'Lexical'},
        {value: 'vector:in-memory', label: 'Vector - In Memory'},
        {value: 'vector:qdrant', label: 'Vector - Qdrant', disabled: qdrantUnavailable}
    ];
}

function qdrantReadiness(status) {
    if (!status?.enabled || !hasQdrantStatus(status)) {
        return null;
    }

    if (status.qdrantReachable === false) {
        return {
            label: 'Qdrant: not running.',
            detail: 'Run ./restart.sh, then use Rebuild Index or Compare Retrieval Targets again.',
            className: 'rag-query-card__readiness-warning'
        };
    }

    if (status.qdrantReachable && status.qdrantCollectionExists === false) {
        return {
            label: 'Qdrant: index missing.',
            detail: 'Click Rebuild Index before using Vector - Qdrant.',
            className: 'rag-query-card__readiness-warning'
        };
    }

    if (status.qdrantReachable && status.qdrantCollectionExists) {
        return {
            label: 'Qdrant: ready.',
            detail: typeof status.qdrantPointCount === 'number'
                ? `${status.qdrantPointCount} indexed chunks are available for Vector - Qdrant.`
                : 'Vector - Qdrant is available.',
            className: 'rag-query-card__readiness-ok'
        };
    }

    return {
        label: 'Qdrant: not checked.',
        detail: 'Use Compare Retrieval Targets to verify Vector - Qdrant when needed.',
        className: 'rag-query-card__readiness-neutral'
    };
}

function hasQdrantStatus(status) {
    return Boolean(status?.qdrantRequired)
        || Object.hasOwn(status || {}, 'qdrantReachable')
        || Object.hasOwn(status || {}, 'qdrantCollectionExists')
        || Boolean(status?.qdrantUrl);
}

function formatRetrievalTargetLabel(value) {
    const option = retrievalTargetOptions(null).find((candidate) => candidate.value === value);
    return option?.label || formatRagStatusValue(value);
}

function comparisonStatusLabel(result) {
    if (result?.success) {
        return 'Success';
    }
    if (String(result?.retrievalTarget || '').toLowerCase() === 'vector:qdrant'
            && /not reachable|not running|connection refused|connect/i.test(String(result?.error || ''))) {
        return 'Unavailable';
    }
    return 'Failed';
}

function formatIndexStatus(status) {
    if (!status?.enabled) {
        return 'disabled';
    }
    return status.indexed ? 'ready' : 'will index on first question';
}

function formatIndexCount(status, count) {
    if (status?.enabled && !status.indexed) {
        return 'not loaded yet';
    }
    return count;
}

function retrievalModeHint(status) {
    if (isVectorRetrieval(status)) {
        return 'Vector mode uses semantic search over the local docs. This mode uses embeddings to find related content even when the wording is different.';
    }
    return 'Lexical mode uses keyword search over the local docs. It is the simplest mode and works without embeddings or a vector database.';
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

function createRagAnswerMessage(payload, selectedProvider, selectedModel) {
    return {
        role: 'assistant',
        content: payload.answer,
        metadata: {
            provider: payload.metadata?.provider || payload.provider || selectedProvider,
            modelId: payload.metadata?.modelId || payload.model || selectedModel
        },
        ragSources: payload.sources || [],
        ragRetrieval: payload.ragRetrieval || null,
        ragTiming: payload.ragTiming || null,
        timestamp: new Date().toISOString()
    };
}

export default RagWorkspace;
