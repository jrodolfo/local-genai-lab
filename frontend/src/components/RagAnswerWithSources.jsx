function RagAnswerWithSources({ result }) {
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
