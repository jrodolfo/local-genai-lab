/**
 * @fileoverview InputBox component for composing and sending chat messages.
 * Includes provider and model selection, and streaming toggle.
 */
import {useState} from 'react';

/**
 * InputBox component.
 *
 * @param {Object} props - Component props.
 * @param {boolean} props.disabled - Whether the entire input box is disabled.
 * @param {boolean} [props.loading=false] - Whether a message is currently being sent/processed.
 * @param {string} [props.loadingMessage=''] - Primary status message during loading.
 * @param {string} [props.loadingDetail=''] - Secondary status detail during loading.
 * @param {string} [props.loadingHint=''] - Hint or action during loading.
 * @param {string} [props.statusMessage=''] - Status message to show when not loading.
 * @param {boolean} [props.modelsLoading=false] - Whether provider model options are loading.
 * @param {string[]} [props.providers=[]] - List of available provider IDs.
 * @param {string} [props.selectedProvider=''] - Currently selected provider ID.
 * @param {Function} props.onProviderChange - Callback when the provider selection changes.
 * @param {string[]} [props.models=[]] - List of available model IDs for the selected provider.
 * @param {string} [props.selectedModel=''] - Currently selected model ID.
 * @param {Function} props.onModelChange - Callback when the model selection changes.
 * @param {boolean} [props.streaming=true] - Whether streaming mode is enabled.
 * @param {Function} props.onStreamingChange - Callback when the streaming toggle changes.
 * @param {Function} props.onSend - Callback when the user submits a message.
 * @param {Function} props.onCancel - Callback when the user cancels a loading request.
 * @returns {React.JSX.Element} The rendered InputBox component.
 */
function InputBox({
                      disabled,
                      loading = false,
                      loadingMessage = '',
                      loadingDetail = '',
                      loadingHint = '',
                      statusMessage = '',
                      modelsLoading = false,
                      providers = [],
                      selectedProvider = '',
                      onProviderChange,
                      models = [],
                      selectedModel = '',
                      onModelChange,
                      streaming = true,
                      onStreamingChange,
                      onSend,
                      onCancel
                  }) {
    const [message, setMessage] = useState('');
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
                        {modelsLoading ? (
                            <option value="" disabled>
                                Loading models...
                            </option>
                        ) : null}
                        {!modelsLoading && models.length === 0 ? (
                            <option value="" disabled>
                                No models available
                            </option>
                        ) : null}
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
                        onChange={(event) => onStreamingChange(event.target.checked)}
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
