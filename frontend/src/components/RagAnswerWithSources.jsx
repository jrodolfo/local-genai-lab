/**
 * @fileoverview RagAnswerWithSources component for displaying a RAG query answer and its source documents.
 */

/**
 * RagAnswerWithSources component.
 *
 * @param {Object} props - Component props.
 * @param {Object} props.result - The RAG query result object.
 * @param {string} props.result.answer - The generated answer text.
 * @param {string} props.result.provider - The LLM provider used.
 * @param {string} props.result.model - The model ID used.
 * @param {Object} props.result.ragRetrieval - Retrieval metadata used by the answer.
 * @param {Array<Object>} props.result.sources - Array of source objects.
 * @param {boolean} [props.showTechnicalDetails=false] - Whether to show diagnostic metadata.
 * @returns {React.JSX.Element|null} The rendered component or null if no result.
 */
function RagAnswerWithSources({result, showTechnicalDetails = false}) {
    if (!result) {
        return null;
    }

    return (
        <section className="rag-answer-card" aria-label="RAG answer">
            <div className="rag-answer-card__header">
                <h2>Answer</h2>
                <p>
                    {labelForProvider(result.provider)} · {result.model}
                    {!showTechnicalDetails && result.ragRetrieval ? ` · Retrieval: ${labelForRetrieval(result.ragRetrieval)}` : ''}
                </p>
            </div>
            {showTechnicalDetails ? (
                <div className="rag-technical-details" aria-label="RAG technical details">
                    <span>provider: {labelForProvider(result.provider)}</span>
                    <span>model: {result.model || 'unknown'}</span>
                    {result.ragRetrieval ? <span>retrieval: {labelForRetrieval(result.ragRetrieval)}</span> : null}
                    {result.elapsedMs != null ? <span>request elapsed: {formatDuration(result.elapsedMs)}</span> : null}
                </div>
            ) : null}
            <div className="rag-answer-card__body">
                <p>{result.answer}</p>
            </div>
            <div className="rag-answer-card__sources">
                <h3>Sources</h3>
                <ul>
                    {result.sources.map((source) => (
                        <li key={`${source.sourcePath}-${source.excerpt.slice(0, 24)}`}>
                            <div className="rag-source__meta">
                                <strong>{source.title}</strong>
                                <span>{source.sourcePath}</span>
                                {showTechnicalDetails ? <span>score {source.score}</span> : null}
                            </div>
                            <p>{source.excerpt}</p>
                        </li>
                    ))}
                </ul>
            </div>
        </section>
    );
}

/**
 * Formats a provider ID into a human-readable label.
 *
 * @param {string} provider - The provider ID.
 * @returns {string} The formatted label.
 */
function labelForProvider(provider) {
    if (!provider) {
        return 'Provider';
    }
    if (provider === 'huggingface') {
        return 'Hugging Face';
    }
    return provider.charAt(0).toUpperCase() + provider.slice(1);
}

function labelForRetrieval(ragRetrieval) {
    const target = ragRetrieval?.retrievalTarget;
    if (target === 'vector:qdrant') {
        return 'Vector - Qdrant';
    }
    if (target === 'vector:in-memory') {
        return 'Vector - In Memory';
    }
    if (target === 'lexical:in-memory' || ragRetrieval?.retrievalMode === 'lexical') {
        return 'Lexical';
    }
    const mode = ragRetrieval?.retrievalMode || 'unknown';
    const store = ragRetrieval?.vectorStore;
    return store ? `${capitalize(mode)} - ${capitalize(store)}` : capitalize(mode);
}

function capitalize(value) {
    const normalized = String(value || '').replaceAll('-', ' ');
    return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function formatDuration(totalMs) {
    const durationMs = Math.max(0, Math.round(totalMs ?? 0));
    if (durationMs >= 1000) {
        return `${(durationMs / 1000).toFixed(1)} s`;
    }
    return `${durationMs} ms`;
}

export default RagAnswerWithSources;
