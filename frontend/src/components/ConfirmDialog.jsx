/**
 * @fileoverview Reusable confirmation dialog for destructive or important user actions.
 */
import {useEffect, useId, useRef} from 'react';
import './ConfirmDialog.css';

/**
 * Renders an accessible confirmation modal.
 *
 * @param {Object} props - Component props.
 * @param {boolean} props.open - Whether the dialog is visible.
 * @param {string} props.title - Dialog title.
 * @param {string} props.message - Dialog body text.
 * @param {string} [props.confirmLabel='Confirm'] - Confirm button label.
 * @param {string} [props.cancelLabel='Cancel'] - Cancel button label.
 * @param {boolean} [props.danger=false] - Whether the confirm action is destructive.
 * @param {Function} props.onConfirm - Called when the user confirms.
 * @param {Function} props.onCancel - Called when the user cancels.
 * @returns {React.JSX.Element|null} The rendered dialog or null when closed.
 */
function ConfirmDialog({
                           open,
                           title,
                           message,
                           confirmLabel = 'Confirm',
                           cancelLabel = 'Cancel',
                           danger = false,
                           onConfirm,
                           onCancel
                       }) {
    const titleId = useId();
    const messageId = useId();
    const cancelButtonRef = useRef(null);

    useEffect(() => {
        if (!open) {
            return undefined;
        }

        cancelButtonRef.current?.focus();

        function handleKeyDown(event) {
            if (event.key === 'Escape') {
                onCancel();
            }
        }

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [open, onCancel]);

    if (!open) {
        return null;
    }

    return (
        <div className="confirm-dialog-backdrop" onMouseDown={onCancel}>
            <section
                className="confirm-dialog"
                role="alertdialog"
                aria-modal="true"
                aria-labelledby={titleId}
                aria-describedby={messageId}
                onMouseDown={(event) => event.stopPropagation()}
            >
                <div className="confirm-dialog__icon" aria-hidden="true">
                    !
                </div>
                <div className="confirm-dialog__content">
                    <h2 id={titleId}>{title}</h2>
                    <p id={messageId}>{message}</p>
                    <div className="confirm-dialog__actions">
                        <button
                            type="button"
                            className="confirm-dialog__button confirm-dialog__button-cancel"
                            onClick={onCancel}
                            ref={cancelButtonRef}
                        >
                            {cancelLabel}
                        </button>
                        <button
                            type="button"
                            className={`confirm-dialog__button ${danger ? 'confirm-dialog__button-danger' : 'confirm-dialog__button-primary'}`}
                            onClick={onConfirm}
                        >
                            {confirmLabel}
                        </button>
                    </div>
                </div>
            </section>
        </div>
    );
}

export default ConfirmDialog;
