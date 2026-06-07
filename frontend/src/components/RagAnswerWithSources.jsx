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
 * @param {Array<Object>} props.result.sources - Array of source objects.
 * @returns {React.JSX.Element|null} The rendered component or null if no result.
 */
function RagAnswerWithSources({result}) {
    if (!result) {
        return null;
    }

    return (
        <section className="rag-answer-card" aria-label="RAG answer">
            <div className="rag-answer-card__header">
                <h2>Answer</h2>
                <p>
                    {labelForProvider(result.provider)} · {result.model}
                </p>
            </div>
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
                                <span>score {source.score}</span>
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

export default RagAnswerWithSources;
