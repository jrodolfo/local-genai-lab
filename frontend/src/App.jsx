import { useEffect, useState } from 'react';
import { getRagStatus } from './api/ragApi';
import Home from './pages/Home';
import RagWorkspace from './pages/RagWorkspace';
import './App.css';

function App() {
  const [mode, setMode] = useState('chat');
  const [ragEnabled, setRagEnabled] = useState(false);

  useEffect(() => {
    let active = true;
    getRagStatus()
      .then((payload) => {
        if (active) {
          setRagEnabled(Boolean(payload.enabled));
        }
      })
      .catch(() => {
        if (active) {
          setRagEnabled(false);
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
          {ragEnabled ? (
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'rag'}
              aria-current={mode === 'rag' ? 'page' : undefined}
              disabled={mode === 'rag'}
              className={mode === 'rag' ? 'app-nav__tab--active' : ''}
              onClick={() => setMode('rag')}
            >
              Docs RAG
            </button>
          ) : null}
        </div>
      </header>
      {mode === 'rag' && ragEnabled ? <RagWorkspace /> : <Home />}
    </div>
  );
}

export default App;
