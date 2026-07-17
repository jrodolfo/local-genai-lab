/**
 * @fileoverview Home page component that provides the main chat interface.
 * Handles session management, model/provider selection, message sending (streaming/non-streaming),
 * and artifact inspection.
 */
import {useEffect, useRef, useState} from 'react';
import {listArtifacts, previewArtifact} from '../api/artifactApi';
import {sendMessage, streamMessage} from '../api/chatApi';
import {getProviderStatus, listAvailableModels} from '../api/modelApi';
import {retryAsync} from '../api/retry';
import {deleteSession, exportSession, getSession, importSession, listSessions} from '../api/sessionApi';
import ChatWindow from '../components/ChatWindow';
import ConfirmDialog from '../components/ConfirmDialog';
import InputBox from '../components/InputBox';
import {formatSessionDateTime} from '../utils/dateFormat';
import './Home.css';

const DEBUG_MODE_STORAGE_KEY = 'local-genai-lab.debug-mode';
const SLOW_PROVIDER_HINT_THRESHOLD_SECONDS = 10;

/**
 * Home page component.
 *
 * @returns {React.JSX.Element} The rendered Home page.
 */
function Home() {
    const importInputRef = useRef(null);
    const chatWindowRef = useRef(null);
    const activeRequestControllerRef = useRef(null);
    const [messages, setMessages] = useState([]);
    const [sessionId, setSessionId] = useState(null);
    const [pendingTool, setPendingTool] = useState(null);
    const [sessions, setSessions] = useState([]);
    const [showSessionsSidebar, setShowSessionsSidebar] = useState(true);
    const [sessionSearch, setSessionSearch] = useState('');
    const [providerFilter, setProviderFilter] = useState('');
    const [toolUsageFilter, setToolUsageFilter] = useState('');
    const [pendingOnly, setPendingOnly] = useState(false);
    const [loading, setLoading] = useState(false);
    const [loadingMessage, setLoadingMessage] = useState('');
    const [toolLifecycleMessage, setToolLifecycleMessage] = useState('');
    const [loadingStartedAt, setLoadingStartedAt] = useState(null);
    const [loadingElapsedSeconds, setLoadingElapsedSeconds] = useState(0);
    const [availableProviders, setAvailableProviders] = useState([]);
    const [sessionProviderOptions, setSessionProviderOptions] = useState([]);
    const [selectedProvider, setSelectedProvider] = useState('');
    const [availableModels, setAvailableModels] = useState([]);
    const [selectedModel, setSelectedModel] = useState('');
    const [providerStatus, setProviderStatus] = useState(null);
    const [providerStatusRefreshing, setProviderStatusRefreshing] = useState(false);
    const [modelsLoading, setModelsLoading] = useState(true);
    const [modelsLoadFailed, setModelsLoadFailed] = useState(false);
    const [error, setError] = useState('');
    const [pendingDeleteSession, setPendingDeleteSession] = useState(null);
    const [artifactFiles, setArtifactFiles] = useState([]);
    const [artifactPreview, setArtifactPreview] = useState(null);
    const [artifactPanelMode, setArtifactPanelMode] = useState('idle');
    const [artifactPanelTitle, setArtifactPanelTitle] = useState('');
    const [artifactPanelMessage, setArtifactPanelMessage] = useState('');
    const [artifactPanelPath, setArtifactPanelPath] = useState('');
    const [statusNotice, setStatusNotice] = useState('');
    const [showTechnicalDetails, setShowTechnicalDetails] = useState(() => {
        if (typeof window === 'undefined') {
            return false;
        }
        return window.localStorage.getItem(DEBUG_MODE_STORAGE_KEY) === 'true';
    });
    const resetArtifactPanel = () => {
        const nextState = resetArtifactPanelState();
        setArtifactFiles([]);
        setArtifactPreview(null);
        setArtifactPanelMode(nextState.mode);
        setArtifactPanelTitle(nextState.title);
        setArtifactPanelMessage(nextState.message);
        setArtifactPanelPath(nextState.path);
    };

    useEffect(() => {
        // Debounce list filtering so typing in the session search box does not trigger a backend
        // request on every keystroke.
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
        if (!selectedProvider) {
            setProviderStatus(null);
            return;
        }
        loadProviderStatus(selectedProvider);
    }, [selectedProvider]);

    useEffect(() => {
        const chatWindow = chatWindowRef.current;
        if (!chatWindow) {
            return;
        }
        // Keep the latest prompt and streamed reply visible despite the fixed composer at the bottom.
        chatWindow.scrollTo({
            top: chatWindow.scrollHeight,
            behavior: 'auto'
        });
    }, [messages, loading, loadingMessage]);

    /**
     * Appends a local message to the current conversation view.
     *
     * Messages loaded from persisted sessions already have stable backend data.
     * Locally-created messages get a browser UUID so optimistic user/assistant
     * entries can render immediately while the backend request is still running.
     *
     * @param {string} role - Message sender role ('user' or 'assistant').
     * @param {string} content - Message content.
     * @param {Object} [tool=null] - Tool usage metadata.
     * @param {Object} [toolResult=null] - Tool execution result.
     * @param {Object} [metadata=null] - Technical metadata.
     */
    const addMessage = (role, content, tool = null, toolResult = null, metadata = null) => {
        setMessages((current) => [...current, {id: crypto.randomUUID(), role, content, tool, toolResult, metadata}]);
    };

    /**
     * Updates the optimistic assistant message used by streaming responses.
     *
     * @param {(content: string) => string} updater - Function that receives the current assistant text.
     */
    const updateLastAssistant = (updater) => {
        setMessages((current) =>
            current.map((message, index) => {
                if (index !== current.length - 1 || message.role !== 'assistant') {
                    return message;
                }
                return {...message, content: updater(message.content)};
            })
        );
    };

    /**
     * Merges backend metadata into the latest optimistic assistant message.
     *
     * Streaming responses deliver text and metadata independently, so tool
     * results and provider metadata may arrive before or after visible tokens.
     *
     * @param {Object} details - Partial assistant details from a stream event.
     * @param {Object|null} [details.tool] - Tool usage metadata.
     * @param {Object|null} [details.toolResult] - Structured tool result.
     * @param {Object|null} [details.metadata] - Provider metadata.
     */
    const updateLastAssistantDetails = ({tool, toolResult, metadata}) => {
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

    /**
     * Marks a partial streamed answer as canceled, or removes it if no tokens arrived.
     */
    const finalizeCanceledAssistant = () => {
        setMessages((current) => {
            if (current.length === 0) {
                return current;
            }
            const lastMessage = current[current.length - 1];
            if (lastMessage.role !== 'assistant') {
                return current;
            }
            const content = lastMessage.content || '';
            if (!content.trim()) {
                return current.slice(0, -1);
            }
            if (content.includes('[Response canceled.]')) {
                return current;
            }
            return current.map((message, index) => (
                index === current.length - 1
                    ? {...message, content: `${content}\n\n[Response canceled.]`}
                    : message
            ));
        });
    };

    /**
     * Adds browser-observed wait time to the latest assistant message metadata.
     *
     * @param {number} uiWaitMs - Elapsed time between submit and final UI completion.
     */
    const applyUiWaitToLastAssistant = (uiWaitMs) => {
        setMessages((current) =>
            current.map((message, index) => {
                if (index !== current.length - 1 || message.role !== 'assistant') {
                    return message;
                }
                return {
                    ...message,
                    metadata: mergeProviderMetadata(message.metadata, {uiWaitMs})
                };
            })
        );
    };

    /**
     * Fetches and updates the session list based on filters.
     *
     * @param {Object} [filters={}] - Filter criteria.
     * @returns {Promise<void>}
     */
    async function loadSessions(filters = {}) {
        try {
            const payload = await retryAsync(() => listSessions(filters), {retries: 4, delayMs: 500});
            setSessions(payload);
            if (sessionId && !payload.some((session) => session.sessionId === sessionId)) {
                setSessionId(null);
                setPendingTool(null);
                setMessages([]);
                setArtifactFiles([]);
                setArtifactPreview(null);
                resetArtifactPanel();
                setStatusNotice('The active session is no longer available from the backend. The view was reset.');
            }
            if (!filters.provider) {
                setSessionProviderOptions(providerOptionsFromSessions(payload));
            }
            setError((current) => (current === 'Failed to load sessions. Check if the backend is up and running.' ? '' : current));
        } catch (err) {
            setError(err.message || 'Failed to load sessions. Check if the backend is up and running.');
        }
    }

    /**
     * Loads available models for a specific provider (or all providers if none specified).
     *
     * @param {string} [provider] - Optional provider ID.
     * @returns {Promise<void>}
     */
    async function loadAvailableModels(provider) {
        try {
            setModelsLoading(true);
            setModelsLoadFailed(false);
            const payload = await retryAsync(() => listAvailableModels(provider), {retries: 4, delayMs: 500});
            const providers = Array.isArray(payload.providers) ? payload.providers : [];
            const models = Array.isArray(payload.models) ? payload.models : [];
            const defaultModel = payload.defaultModel && models.includes(payload.defaultModel) ? payload.defaultModel : '';
            setAvailableProviders(providers);
            setAvailableModels(models);
            setSelectedProvider((current) => {
                if (provider && providers.includes(provider)) {
                    return provider;
                }
                if (current && providers.includes(current)) {
                    return current;
                }
                if (payload.provider && providers.includes(payload.provider)) {
                    return payload.provider;
                }
                if (payload.defaultProvider && providers.includes(payload.defaultProvider)) {
                    return payload.defaultProvider;
                }
                return providers[0] || payload.provider || payload.defaultProvider || '';
            });
            // Preserve the current selection when still valid; otherwise use the provider default or
            // the first available model.
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
            setAvailableProviders([]);
            setAvailableModels([]);
            setSelectedProvider('');
            setSelectedModel('');
        } finally {
            setModelsLoading(false);
        }
    }

    /**
     * Fetches the status of a specific provider.
     *
     * @param {string} provider - The provider ID.
     * @param {Object} [options={}] - Options.
     * @param {boolean} [options.manual=false] - Whether this was triggered manually by the user.
     * @returns {Promise<void>}
     */
    async function loadProviderStatus(provider, options = {}) {
        const {manual = false} = options;
        if (manual) {
            setProviderStatusRefreshing(true);
        }
        try {
            const payload = await retryAsync(() => getProviderStatus(provider), {
                retries: manual ? 0 : 4,
                delayMs: 500
            });
            setProviderStatus(payload);
        } catch (err) {
            setProviderStatus({
                provider: provider || selectedProvider,
                status: 'unknown',
                message: err.message || 'Failed to load provider status.'
            });
        } finally {
            if (manual) {
                setProviderStatusRefreshing(false);
            }
        }
    }

    const startNewChat = () => {
        setSessionId(null);
        setPendingTool(null);
        setMessages([]);
        setArtifactFiles([]);
        setArtifactPreview(null);
        resetArtifactPanel();
        setError('');
    };

    const handleImportClick = () => {
        importInputRef.current?.click();
    };

    /**
     * Imports a JSON session export and opens the stored session returned by the backend.
     *
     * @param {Event} event - File input change event.
     * @returns {Promise<void>}
     */
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

    /**
     * Opens a specific session by ID and maps backend messages into renderable UI messages.
     *
     * @param {string} targetSessionId - The session ID to open.
     * @returns {Promise<void>}
     */
    const openSession = async (targetSessionId) => {
        setError('');
        setLoading(true);
        try {
            const payload = await getSession(targetSessionId);
            setSessionId(payload.sessionId);
            setPendingTool(payload.pendingTool || null);
            setArtifactFiles([]);
            setArtifactPreview(null);
            resetArtifactPanel();
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
            if (err?.status === 404) {
                startNewChat();
                setStatusNotice('That saved session is no longer available from the backend.');
            }
            setError(err.message || 'Failed to load session.');
        } finally {
            setLoading(false);
        }
    };

    /**
     * Marks a session for deletion and opens the confirmation dialog.
     *
     * @param {Object} session - Session summary selected for deletion.
     * @returns {void}
     */
    const requestSessionDeletion = (session) => {
        setPendingDeleteSession({
            sessionId: session.sessionId,
            title: session.title
        });
    };

    /**
     * Deletes the pending session and resets the view if it was active.
     *
     * @returns {Promise<void>}
     */
    const confirmPendingSessionDeletion = async () => {
        const targetSession = pendingDeleteSession;
        if (!targetSession) {
            return;
        }
        setPendingDeleteSession(null);
        setError('');
        setLoading(true);
        try {
            await deleteSession(targetSession.sessionId);
            setSessions((current) => current.filter((session) => session.sessionId !== targetSession.sessionId));
            if (sessionId === targetSession.sessionId) {
                startNewChat();
            }
        } catch (err) {
            setError(err.message || 'Failed to delete session.');
        } finally {
            setLoading(false);
        }
    };

    /**
     * Triggers a file download for a session export.
     *
     * @param {string} targetSessionId - The session ID to export.
     * @param {string} [format='json'] - Export format ('json' or 'markdown').
     * @returns {Promise<void>}
     */
    const downloadSession = async (targetSessionId, format = 'json') => {
        setError('');
        setLoading(true);
        try {
            const {blob, filename} = await exportSession(targetSessionId, format);
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

    /**
     * Lists all artifact files in a specific tool run directory.
     *
     * The artifact panel keeps its own empty/error state so a missing restored
     * run directory can be explained without clearing the chat transcript.
     *
     * @param {string} runDir - The backend-visible run directory path.
     * @param {string} [title='artifact files'] - Title for the artifact panel.
     * @returns {Promise<void>}
     */
    const handleListArtifacts = async (runDir, title = 'artifact files') => {
        setError('');
        setLoading(true);
        try {
            const payload = await listArtifacts(runDir);
            setArtifactPanelMode('files');
            setArtifactPanelTitle(formatArtifactPanelTitle(title, 'files'));
            setArtifactPanelMessage('');
            setArtifactPanelPath(runDir);
            setArtifactFiles(payload);
            setArtifactPreview(null);
        } catch (err) {
            const panelMessage = resolveArtifactPanelErrorMessage(err, 'files');
            setArtifactPanelMode('files');
            setArtifactPanelTitle(formatArtifactPanelTitle(title, 'files'));
            setArtifactPanelMessage(panelMessage);
            setArtifactPanelPath(runDir);
            setArtifactFiles([]);
            setArtifactPreview(null);
            setError(err.message || 'Failed to list artifact files.');
        } finally {
            setLoading(false);
        }
    };

    /**
     * Previews a specific artifact file in the side panel.
     *
     * @param {string} path - Backend-visible full path to the artifact.
     * @param {string} [title='artifact preview'] - Title for the artifact panel.
     * @returns {Promise<void>}
     */
    const handlePreviewArtifact = async (path, title = 'artifact preview') => {
        setError('');
        setLoading(true);
        try {
            const payload = await previewArtifact(path);
            setArtifactPanelMode('preview');
            setArtifactPanelTitle(formatArtifactPanelTitle(title, 'preview'));
            setArtifactPanelMessage('');
            setArtifactPanelPath(path);
            setArtifactPreview(payload);
            setArtifactFiles([]);
        } catch (err) {
            const panelMessage = resolveArtifactPanelErrorMessage(err, 'preview');
            setArtifactPanelMode('preview');
            setArtifactPanelTitle(formatArtifactPanelTitle(title, 'preview'));
            setArtifactPanelMessage(panelMessage);
            setArtifactPanelPath(path);
            setArtifactPreview(null);
            setArtifactFiles([]);
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

    const handleProviderChange = async (provider) => {
        setSelectedProvider(provider);
        setSelectedModel('');
        await loadAvailableModels(provider);
    };

    const resetLoadingState = () => {
        setLoading(false);
        setLoadingMessage('');
        setToolLifecycleMessage('');
        setLoadingStartedAt(null);
        activeRequestControllerRef.current = null;
    };

    const handleCancelRequest = () => {
        activeRequestControllerRef.current?.abort();
    };

    /**
     * Sends one Agent message through the selected backend mode.
     *
     * Non-streaming requests append the assistant message after the complete
     * payload returns. Streaming requests create an empty assistant placeholder
     * first, then merge typed SSE events into that placeholder as tool phases,
     * token deltas, metadata, and completion events arrive.
     *
     * @param {Object} params - Send parameters.
     * @param {string} params.message - The prompt text.
     * @param {string} params.provider - Selected provider.
     * @param {string} params.model - Selected model.
     * @param {boolean} params.streaming - Whether to use SSE streaming.
     * @returns {Promise<void>}
     */
    const handleSend = async ({message, provider, model, streaming}) => {
        const requestStartedAt = Date.now();
        const requestController = new AbortController();
        activeRequestControllerRef.current = requestController;
        setError('');
        setStatusNotice('');
        setLoading(true);
        setLoadingMessage(streaming ? 'Waiting for streamed response...' : 'Waiting for response...');
        setToolLifecycleMessage('');
        setLoadingStartedAt(Date.now());
        addMessage('user', message);

        try {
            if (!streaming) {
                const payload = await sendMessage({
                    message,
                    provider,
                    model,
                    sessionId,
                    signal: requestController.signal
                });
                setToolLifecycleMessage(resolveToolLifecycleMessage({
                    tool: payload.tool,
                    pendingTool: payload.pendingTool
                }));
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
                    provider,
                    model,
                    sessionId,
                    signal: requestController.signal,
                    onEvent: (event) => {
                        if (isToolPhaseEvent(event?.type)) {
                            setToolLifecycleMessage(resolveStreamingToolPhaseMessage(event));
                            return;
                        }

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
            if (err?.name === 'AbortError') {
                if (streaming) {
                    finalizeCanceledAssistant();
                }
                setStatusNotice('Request canceled.');
                return;
            }
            if (toolLifecycleMessage) {
                setToolLifecycleMessage('Tool execution failed before the final answer was generated.');
            }
            setError(err.message || 'Something went wrong.');
            addMessage('assistant', 'Error calling backend/Ollama. Check backend logs.');
        } finally {
            resetLoadingState();
        }
    };

    const loadingStatusMessage = loadingMessage
        ? `${loadingMessage} ${formatElapsedTime(loadingElapsedSeconds)}`
        : '';
    const slowProviderHint = loading && loadingElapsedSeconds >= SLOW_PROVIDER_HINT_THRESHOLD_SECONDS
        ? 'This provider is taking longer than usual.'
        : '';
    const providerFilterOptions = mergeProviderOptions(availableProviders, sessionProviderOptions);

    return (
        <main className="home-page">
            <section className={`chat-layout ${showSessionsSidebar ? '' : 'sidebar-hidden'}`.trim()}>
                {showSessionsSidebar ? (
                    <aside id="sessions-sidebar" className="session-sidebar">
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
                                <button type="button" className="page-action-button" onClick={handleImportClick}
                                        disabled={loading}>
                                    Import JSON
                                </button>
                                <button type="button" className="page-action-button" onClick={startNewChat}
                                        disabled={loading}>
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
                                    {providerFilterOptions.map((provider) => (
                                        <option key={`provider-filter-${provider}`} value={provider}>
                                            {formatProviderName(provider)}
                                        </option>
                                    ))}
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
                                        {session.summary ?
                                            <span className="session-summary">{session.summary}</span> : null}
                                        <span
                                            className="session-meta">{formatSessionDateTime(session.updatedAt)}</span>
                                    </button>
                                    <button
                                        type="button"
                                        className="page-action-button"
                                        onClick={() => downloadSession(session.sessionId, 'json')}
                                        disabled={loading}
                                        aria-label={`Export session ${session.title}`}
                                    >
                                        Export JSON
                                    </button>
                                    <button
                                        type="button"
                                        className="page-action-button"
                                        onClick={() => downloadSession(session.sessionId, 'markdown')}
                                        disabled={loading}
                                        aria-label={`Export markdown session ${session.title}`}
                                    >
                                        Export Markdown
                                    </button>
                                    <button
                                        type="button"
                                        className="page-action-button page-action-button-danger"
                                        onClick={() => requestSessionDeletion(session)}
                                        disabled={loading}
                                        aria-label={`Delete session ${session.title}`}
                                    >
                                        Delete
                                    </button>
                                </div>
                            ))}
                        </div>
                    </aside>
                ) : null}

                <section className="chat-card">
                    <header>
                        <div>
                            <h1>Agent</h1>
                        </div>
                        <div className="header-controls">
                            <label className="debug-toggle">
                                <input
                                    type="checkbox"
                                    checked={showTechnicalDetails}
                                    onChange={(event) => setShowTechnicalDetails(event.target.checked)}
                                />
                                <span>show technical details</span>
                            </label>
                            <button
                                type="button"
                                className="page-action-button sidebar-toggle"
                                aria-expanded={showSessionsSidebar}
                                aria-controls="sessions-sidebar"
                                onClick={() => setShowSessionsSidebar((current) => !current)}
                            >
                                {showSessionsSidebar ? 'Hide Sessions' : 'Show Sessions'}
                            </button>
                        </div>
                    </header>

                    {error ? <div className="error-banner">{error}</div> : null}

                    {providerStatus ? (
                        <div className={`provider-status-banner provider-status-${providerStatus.status}`}>
                            <strong>{`${formatProviderName(providerStatus.provider)} status: ${formatProviderStatus(providerStatus.status)}`}</strong>
                            <span>{providerStatus.message}</span>
                            {providerStatus.configuredModels?.length ? (
                                <div className="provider-status-details">
                                    <span>{`Configured: ${providerStatus.configuredModels.join(', ')}`}</span>
                                    {providerStatus.usableModels?.length ? (
                                        <span>{`Usable: ${providerStatus.usableModels.join(', ')}`}</span>
                                    ) : null}
                                    {providerStatus.rejectedModels?.length ? (
                                        <span>{`Rejected: ${providerStatus.rejectedModels.join(', ')}`}</span>
                                    ) : null}
                                </div>
                            ) : null}
                            {providerStatus.refreshedAt ? (
                                <span className="provider-status-refreshed">
                {`Last checked: ${new Date(providerStatus.refreshedAt).toLocaleString()}`}
              </span>
                            ) : null}
                            <button
                                type="button"
                                className="page-action-button provider-status-refresh"
                                onClick={() => loadProviderStatus(selectedProvider, {manual: true})}
                                disabled={providerStatusRefreshing || !selectedProvider}
                            >
                                {providerStatusRefreshing ? 'Refreshing...' : 'Refresh status'}
                            </button>
                        </div>
                    ) : null}

                    {pendingTool ? (
                        <div className="pending-tool-banner">
                            <strong>awaiting input for tool:</strong> {pendingTool.toolName}
                            {pendingTool.missingFields?.length ? (
                                <span>missing: {pendingTool.missingFields.join(', ')}</span>
                            ) : null}
                        </div>
                    ) : null}

                    <section className="artifact-panel">
                        <div className="artifact-panel-header">
                            <div>
                                <strong>{artifactPanelTitle || 'Artifact inspector'}</strong>
                                {artifactPanelMode === 'idle' ? (
                                    <span>Select a summary, report, or file list to inspect artifacts.</span>
                                ) : null}
                                {artifactPreview?.relativePath ?
                                    <span>{`Relative path: ${artifactPreview.relativePath}`}</span> : null}
                                {!artifactPreview?.relativePath && artifactPanelPath ?
                                    <span>{`Path: ${artifactPanelPath}`}</span> : null}
                            </div>
                            {artifactPanelMode === 'idle' ? null : (
                                <button
                                    type="button"
                                    onClick={resetArtifactPanel}
                                >
                                    Close
                                </button>
                            )}
                        </div>
                        {artifactPanelMode === 'idle' ? (
                            <div className="artifact-panel-empty">
                                <span>Select a summary, report, or file list to inspect artifacts.</span>
                            </div>
                        ) : null}
                        {artifactPanelMode === 'files' && artifactPanelMessage ? (
                            <div className="artifact-panel-empty">
                                <span>{artifactPanelMessage}</span>
                            </div>
                        ) : null}
                        {artifactPanelMode === 'files' && !artifactPanelMessage && artifactFiles.length === 0 ? (
                            <div className="artifact-panel-empty">
                                <span>No files were found in this run directory.</span>
                            </div>
                        ) : null}
                        {artifactPanelMode === 'files' && artifactFiles.length > 0 ? (
                            <div className="artifact-file-list">
                                {artifactFiles.map((file) => (
                                    <div key={file.path} className="artifact-file-item">
                                        <span>{file.relativePath}</span>
                                        <div className="artifact-file-actions">
                                            {file.previewable ? (
                                                <button type="button"
                                                        onClick={() => handlePreviewArtifact(file.path, 'Artifact preview')}>
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
                        {artifactPanelMode === 'preview' && artifactPanelMessage ? (
                            <div className="artifact-panel-empty">
                                <span>{artifactPanelMessage}</span>
                            </div>
                        ) : null}
                        {artifactPanelMode === 'preview' && !artifactPanelMessage && !artifactPreview ? (
                            <div className="artifact-panel-empty">
                                <span>No preview content is available for this artifact.</span>
                            </div>
                        ) : null}
                        {artifactPreview ? (
                            <div className="artifact-preview">
                                <div className="artifact-preview-meta">
                                    <span>{`File: ${artifactPreview.fileName}`}</span>
                                    <span>{`Content type: ${artifactPreview.contentType}`}</span>
                                    <span>{`Size: ${artifactPreview.size} bytes`}</span>
                                    {artifactPreview.truncated ? <span>Preview truncated</span> : null}
                                </div>
                                <pre>{artifactPreview.content}</pre>
                            </div>
                        ) : null}
                    </section>

                    <ChatWindow
                        ref={chatWindowRef}
                        messages={messages}
                        showTechnicalDetails={showTechnicalDetails}
                        onPreviewArtifact={handlePreviewArtifact}
                        onListArtifacts={handleListArtifacts}
                        onCopyPath={handleCopyPath}
                    />

                    <InputBox
                        disabled={modelsLoading}
                        loading={loading}
                        loadingMessage={loading ? loadingStatusMessage : modelsLoading ? 'Loading available models...' : ''}
                        loadingDetail={loading ? toolLifecycleMessage : ''}
                        loadingHint={slowProviderHint}
                        statusMessage={
                            statusNotice || (
                                !modelsLoading && !modelsLoadFailed && availableModels.length === 0
                                    ? selectedProvider === 'ollama'
                                        ? 'No Ollama models are installed locally. Run ollama pull llama3:8b and refresh.'
                                        : 'No models are configured for the active provider.'
                                    : ''
                            )
                        }
                        providers={availableProviders}
                        selectedProvider={selectedProvider}
                        onProviderChange={handleProviderChange}
                        models={availableModels}
                        selectedModel={selectedModel}
                        onModelChange={setSelectedModel}
                        onSend={handleSend}
                        onCancel={handleCancelRequest}
                    />
                </section>
            </section>
            <ConfirmDialog
                open={Boolean(pendingDeleteSession)}
                title="Delete session?"
                message={pendingDeleteSession
                    ? `This will permanently delete "${pendingDeleteSession.title || 'this session'}". This action cannot be undone.`
                    : ''}
                confirmLabel="Delete Session"
                cancelLabel="Keep Session"
                danger={true}
                onConfirm={confirmPendingSessionDeletion}
                onCancel={() => setPendingDeleteSession(null)}
            />
        </main>
    );
}

function resetArtifactPanelState() {
    return {
        mode: 'idle',
        title: '',
        message: '',
        path: ''
    };
}

function providerOptionsFromSessions(sessions) {
    return mergeProviderOptions(
        (sessions || [])
            .map((session) => session?.provider)
            .filter((provider) => typeof provider === 'string' && provider.trim())
    );
}

function mergeProviderOptions(...providerLists) {
    const providers = new Set();
    for (const providerList of providerLists) {
        for (const provider of providerList || []) {
            if (typeof provider === 'string' && provider.trim()) {
                providers.add(provider.trim().toLowerCase());
            }
        }
    }
    return [...providers].sort((left, right) => formatProviderName(left).localeCompare(formatProviderName(right)));
}

function formatArtifactPanelTitle(title, mode) {
    const normalized = (title || '').trim().toLowerCase();
    if (!normalized) {
        return mode === 'files' ? 'Artifact files' : 'Artifact preview';
    }
    if (normalized === 'summary preview') {
        return 'Summary preview';
    }
    if (normalized === 'report preview') {
        return 'Report preview';
    }
    if (normalized === 'artifact preview') {
        return 'Artifact preview';
    }
    if (normalized === 'artifact files') {
        return 'Files in run directory';
    }
    if (normalized === 'failed step stderr') {
        return 'Failed step stderr';
    }
    return title.charAt(0).toUpperCase() + title.slice(1);
}

function resolveArtifactPanelErrorMessage(error, mode) {
    if (error?.status === 404) {
        if (mode === 'files') {
            return 'This run directory is no longer available on disk.';
        }
        return 'This artifact is no longer available on disk.';
    }
    if (mode === 'files') {
        return error?.message || 'Failed to load artifact files.';
    }
    return error?.message || 'Failed to load preview content.';
}

function isToolPhaseEvent(type) {
    return [
        'tool-decision-started',
        'tool-execution-started',
        'tool-execution-completed',
        'answer-generation-started'
    ].includes(type);
}

function resolveStreamingToolPhaseMessage(event) {
    switch (event?.type) {
        case 'tool-decision-started':
            return 'Checking whether a tool is needed...';
        case 'tool-execution-started':
            return `Running tool: ${event.toolName || 'selected tool'}`;
        case 'tool-execution-completed':
        case 'answer-generation-started':
            return 'Preparing the final answer from tool results...';
        default:
            return '';
    }
}

function resolveToolLifecycleMessage({tool, pendingTool}) {
    if (pendingTool) {
        return 'Awaiting additional tool input...';
    }
    if (!tool?.used) {
        return '';
    }
    if (tool.status === 'clarification-needed') {
        return 'Awaiting additional tool input...';
    }
    if (tool.status === 'failed') {
        return 'Tool execution failed before the final answer was generated.';
    }
    if (tool.status === 'partial-success') {
        return 'Tool results include failures; preparing the final answer from partial results.';
    }
    if (tool.status === 'success') {
        return 'Preparing the final answer from tool results...';
    }
    return `Running tool: ${tool.name}`;
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
    if (provider.toLowerCase() === 'huggingface') {
        return 'Hugging Face';
    }
    return provider;
}

function formatProviderStatus(status) {
    if (!status) {
        return 'Unknown';
    }
    return status.replaceAll('_', ' ');
}

export default Home;
