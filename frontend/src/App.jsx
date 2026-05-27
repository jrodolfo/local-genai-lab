import { useEffect, useState } from 'react';
import { getRagStatus } from './api/ragApi';
import { retryAsync } from './api/retry';
import Home from './pages/Home';
import RagWorkspace from './pages/RagWorkspace';
import './App.css';

function App() {
  const [mode, setMode] = useState('chat');
  const [ragEnabled, setRagEnabled] = useState(false);
  const [ragStatusLoaded, setRagStatusLoaded] = useState(false);

  useEffect(() => {
    let active = true;
    retryAsync(() => getRagStatus(), { retries: 8, delayMs: 500 })
      .then((payload) => {
        if (active) {
          setRagEnabled(Boolean(payload.enabled));
          setRagStatusLoaded(true);
        }
      })
      .catch(() => {
        if (active) {
          setRagEnabled(false);
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
        <div className="app-nav__brand">Local GenAI Lab</div>
        <div className="app-nav__tabs" role="tablist" aria-label="Application modes">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'chat'}
            aria-current={mode === 'chat' ? 'page' : undefined}
            disabled={mode === 'chat'}
            className={mode === 'chat' ? 'app-nav__tab--active' : ''}
            onClick={() => setMode('chat')}
          >
            Chat
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'rag'}
            aria-current={mode === 'rag' ? 'page' : undefined}
            aria-disabled={!ragEnabled}
            disabled={mode === 'rag' || !ragEnabled}
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
        </div>
      </header>
      {ragStatusLoaded && !ragEnabled ? (
        <p className="app-nav__hint">Enable `RAG_ENABLED=true` in the backend to use RAG mode.</p>
      ) : null}
      {mode === 'rag' && ragEnabled ? <RagWorkspace /> : <Home />}
    </div>
  );
}

export default App;
