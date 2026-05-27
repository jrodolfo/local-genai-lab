import { useEffect, useState } from 'react';
import { listAvailableModels } from '../api/modelApi';
import { getRagStatus, queryRag, rebuildRagIndex } from '../api/ragApi';
import RagAnswerWithSources from '../components/RagAnswerWithSources';
import './RagWorkspace.css';

function RagWorkspace() {
  const [ragStatus, setRagStatus] = useState(null);
  const [availableProviders, setAvailableProviders] = useState([]);
  const [availableModels, setAvailableModels] = useState([]);
  const [selectedProvider, setSelectedProvider] = useState('');
  const [selectedModel, setSelectedModel] = useState('');
  const [question, setQuestion] = useState('');
  const [result, setResult] = useState(null);
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
      const [statusPayload, modelsPayload] = await Promise.all([
        getRagStatus(),
        listAvailableModels()
      ]);
      setRagStatus(statusPayload);
      hydrateProviders(modelsPayload);
    } catch (err) {
      setError(err.message || 'Failed to load the RAG workspace.');
    } finally {
      setLoading(false);
    }
  }

  async function loadModelsForProvider(provider) {
    try {
      const payload = await listAvailableModels(provider);
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
        model: selectedModel
      });
      setResult(payload);
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

  return (
    <main className="rag-page">
      <section className="rag-hero">
        <div>
          <p className="rag-eyebrow">Experimental RAG</p>
          <h1>Chat with the project docs</h1>
          <p>
            This workspace retrieves chunks from the local docs corpus, sends them to the selected
            provider, and returns an answer with cited sources.
          </p>
        </div>
        <div className="rag-status-card">
          <h2>Index status</h2>
          {loading ? <p>Loading RAG status...</p> : null}
          {!loading && ragStatus ? (
            <>
              <p>Status: {ragStatus.enabled ? (ragStatus.indexed ? 'ready' : 'not indexed yet') : 'disabled'}</p>
              <p>Corpus: {ragStatus.corpusRoot}</p>
              <p>Documents: {ragStatus.documentCount}</p>
              <p>Chunks: {ragStatus.chunkCount}</p>
              <p>Retrieval: {ragStatus.retrievalMode}</p>
              {ragStatus.enabled ? (
                <button type="button" onClick={handleRebuildIndex} disabled={rebuilding}>
                  {rebuilding ? 'Rebuilding index...' : 'Rebuild index'}
                </button>
              ) : null}
            </>
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

          {result ? (
            <RagAnswerWithSources result={result} />
          ) : (
            <section className="rag-empty-state">
              <h2>No answer yet</h2>
              <p>Ask a question to retrieve the most relevant doc chunks and generate a cited answer.</p>
            </section>
          )}
        </section>
      ) : null}
    </main>
  );
}

export default RagWorkspace;
