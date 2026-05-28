import {useState} from 'react';

function InputBox({
                      disabled,
                      loading = false,
                      loadingMessage = '',
                      loadingDetail = '',
                      loadingHint = '',
                      statusMessage = '',
                      providers = [],
                      selectedProvider = '',
                      onProviderChange,
                      models = [],
                      selectedModel = '',
                      onModelChange,
                      onSend,
                      onCancel
                  }) {
    const [message, setMessage] = useState('');
    const [streaming, setStreaming] = useState(true);
    const controlsDisabled = disabled || loading;
    const sendDisabled = controlsDisabled || !selectedModel;

    const submit = (event) => {
        event.preventDefault();
        const trimmed = message.trim();
        if (!trimmed || !selectedModel) {
            return;
        }

        onSend({message: trimmed, provider: selectedProvider, model: selectedModel, streaming});
        setMessage('');
    };

    return (
        <form className="input-box" onSubmit={submit}>
            <div className="controls-row">
                <label className="control-field">
                    <span>Provider</span>
                    <select
                        aria-label="Chat provider"
                        value={selectedProvider}
                        onChange={(event) => onProviderChange(event.target.value)}
                        disabled={controlsDisabled}
                    >
                        {providers.map((option) => (
                            <option key={option} value={option}>
                                {option}
                            </option>
                        ))}
                    </select>
                </label>

                <label className="control-field">
                    <span>Model</span>
                    <select
                        aria-label="Model"
                        value={selectedModel}
                        onChange={(event) => onModelChange(event.target.value)}
                        disabled={sendDisabled}
                    >
                        {models.map((option) => (
                            <option key={option} value={option}>
                                {option}
                            </option>
                        ))}
                    </select>
                </label>

                <label className="checkbox-wrap">
                    <input
                        type="checkbox"
                        checked={streaming}
                        onChange={(event) => setStreaming(event.target.checked)}
                        disabled={sendDisabled}
                    />
                    Streaming
                </label>
            </div>

            <div className="composer-row">
        <textarea
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            placeholder="Type your prompt..."
            rows={3}
            disabled={sendDisabled}
        />
                {loading ? (
                    <button type="button" onClick={onCancel}>
                        Cancel
                    </button>
                ) : (
                    <button type="submit" disabled={sendDisabled || !message.trim()}>
                        Send
                    </button>
                )}
            </div>
            {loading && loadingMessage ? <p className="input-status">{loadingMessage}</p> : null}
            {loading && loadingDetail ? <p className="input-status">{loadingDetail}</p> : null}
            {loading && loadingHint ? <p className="input-status">{loadingHint}</p> : null}
            {!loading && statusMessage ? <p className="input-status">{statusMessage}</p> : null}
        </form>
    );
}

export default InputBox;
