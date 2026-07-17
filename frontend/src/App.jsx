/**
 * @fileoverview Main App component that handles navigation between RAG and Agent modes.
 * It also checks if RAG is enabled in the backend on mount.
 */
import {useEffect, useState} from 'react';
import {listAvailableModels} from './api/modelApi';
import {getRagStatus} from './api/ragApi';
import {retryAsync} from './api/retry';
import Home from './pages/Home';
import RagWorkspace from './pages/RagWorkspace';
import './App.css';

/**
 * Root component of the application.
 * Manages the application mode ('chat' or 'rag') and RAG availability state.
 *
 * @returns {React.JSX.Element} The rendered App component.
 */
function App() {
    const [mode, setMode] = useState(null);
    const [instanceName, setInstanceName] = useState('');
    const [ragEnabled, setRagEnabled] = useState(false);
    const [ragStatusLoaded, setRagStatusLoaded] = useState(false);
    const [ragStatusError, setRagStatusError] = useState(false);

    useEffect(() => {
        let active = true;
        listAvailableModels()
            .then((payload) => {
                if (active) {
                    setInstanceName(normalizeInstanceName(payload.instanceName));
                }
            })
            .catch(() => {
                if (active) {
                    setInstanceName('');
                }
            });
        retryAsync(() => getRagStatus(), {retries: 8, delayMs: 500})
            .then((payload) => {
                if (active) {
                    setRagEnabled(Boolean(payload.enabled));
                    setMode(Boolean(payload.enabled) ? 'rag' : 'chat');
                    setRagStatusError(false);
                    setRagStatusLoaded(true);
                }
            })
            .catch(() => {
                if (active) {
                    setRagEnabled(false);
                    setMode('chat');
                    setRagStatusError(true);
                    setRagStatusLoaded(true);
                }
            });
        return () => {
            active = false;
        };
    }, []);

    return (
        <div className="app-shell">
            <header className="app-nav">
                <div className="app-nav__credits" aria-label="Project attribution">
                    <span>© 2026 </span>
                    <a href="https://jrodolfo.net/" target="_blank" rel="noreferrer">Rod Oliveira</a>
                    <span aria-hidden="true"> | </span>
                    <a href="https://github.com/jrodolfo/local-genai-lab/blob/main/LICENSE" target="_blank" rel="noreferrer">
                        MIT License
                    </a>
                    <span aria-hidden="true"> | </span>
                    <a href="https://github.com/jrodolfo/local-genai-lab" target="_blank" rel="noreferrer">
                        GitHub Repo
                    </a>
                </div>
                <div className="app-nav__brand">Local GenAI Lab</div>
                <div className="app-nav__actions">
                    {instanceName ? (
                        <p className="app-nav__instance" aria-label={`Instance: ${instanceName}`}>
                            <span>Instance</span>
                            <strong>{instanceName}</strong>
                        </p>
                    ) : null}
                    <div className="app-nav__tabs" role="tablist" aria-label="Application modes">
                        <button
                            type="button"
                            role="tab"
                            aria-selected={mode === 'rag'}
                            aria-current={mode === 'rag' ? 'page' : undefined}
                            aria-disabled={!ragEnabled}
                            disabled={!ragStatusLoaded || mode === 'rag' || !ragEnabled}
                            className={`${mode === 'rag' ? 'app-nav__tab--active' : ''} ${!ragEnabled ? 'app-nav__tab--disabled' : ''}`.trim()}
                            onClick={() => {
                                if (!ragEnabled) {
                                    return;
                                }
                                setMode('rag');
                            }}
                        >
                            RAG
                        </button>
                        <button
                            type="button"
                            role="tab"
                            aria-selected={mode === 'chat'}
                            aria-current={mode === 'chat' ? 'page' : undefined}
                            disabled={!ragStatusLoaded || mode === 'chat'}
                            className={mode === 'chat' ? 'app-nav__tab--active' : ''}
                            onClick={() => setMode('chat')}
                        >
                            Agent
                        </button>
                    </div>
                </div>
            </header>
            {ragStatusLoaded && !ragEnabled && ragStatusError ? (
                <p className="app-nav__hint">RAG status is temporarily unavailable. Refresh after the backend is ready.</p>
            ) : null}
            {ragStatusLoaded && !ragEnabled && !ragStatusError ? (
                <p className="app-nav__hint">Enable `RAG_ENABLED=true` in the backend to use RAG mode.</p>
            ) : null}
            {ragStatusLoaded ? (
                mode === 'rag' && ragEnabled ? <RagWorkspace/> : <Home/>
            ) : (
                <main className="app-loading" aria-label="Application loading">
                    <p>Loading workspace...</p>
                </main>
            )}
        </div>
    );
}

function normalizeInstanceName(value) {
    return typeof value === 'string' ? value.trim() : '';
}

export default App;
