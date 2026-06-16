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
 * @param {Object|null} props.result.ragRetrieval - Retrieval metadata for the answer.
 * @param {Object|null} props.result.ragTiming - Backend timing metadata for the answer.
 * @param {boolean} [props.showTechnicalDetails=false] - Whether to show retrieval and timing metadata.
 * @returns {React.JSX.Element|null} The rendered component or null if no result.
 */
function RagAnswerWithSources({result, showTechnicalDetails = false}) {
    if (!result) {
        return null;
    }

    const technicalRows = buildTechnicalRows(result);

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
            {showTechnicalDetails && technicalRows.length > 0 ? (
                <dl className="rag-technical-details" aria-label="RAG technical details">
                    {technicalRows.map((row) => (
                        <div key={row.label}>
                            <dt>{row.label}</dt>
                            <dd>{row.value}</dd>
                        </div>
                    ))}
                </dl>
            ) : null}
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

function buildTechnicalRows(result) {
    const rows = [];
    const retrieval = result.ragRetrieval || {};
    const timing = result.ragTiming || {};

    addRow(rows, 'Retrieval mode', formatLabel(retrieval.retrievalMode));
    addRow(rows, 'Retrieval target', retrieval.retrievalTarget);
    addRow(rows, 'Retrieval store', formatLabel(retrieval.retrievalStore));
    addRow(rows, 'Vector store', formatLabel(retrieval.vectorStore));
    addRow(rows, 'Top K', retrieval.topK);
    addRow(rows, 'Embedding provider', formatLabel(retrieval.embeddingProvider));
    addRow(rows, 'Embedding model', retrieval.embeddingModel);
    addRow(rows, 'Retrieval duration', formatDuration(timing.retrievalDurationMs));
    addRow(rows, 'Provider duration', formatDuration(timing.providerDurationMs));
    addRow(rows, 'Backend total', formatDuration(timing.totalDurationMs));

    return rows;
}

function addRow(rows, label, value) {
    if (value === null || value === undefined || value === '') {
        return;
    }
    rows.push({label, value: String(value)});
}

function formatDuration(value) {
    if (value === null || value === undefined) {
        return null;
    }
    if (Number(value) === 0) {
        return '<1 ms';
    }
    return `${value} ms`;
}

function formatLabel(value) {
    if (!value) {
        return null;
    }
    const normalized = String(value).replaceAll('-', ' ');
    return normalized.charAt(0).toUpperCase() + normalized.slice(1);
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
