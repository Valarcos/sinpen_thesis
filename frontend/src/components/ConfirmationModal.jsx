import './ConfirmationModal.css';

export default function ConfirmationModal({ title, message, onConfirm, onCancel, confirmText = "Confirmar", cancelText = "Cancelar", isWarning = false, thirdOptionText, onThirdOption }) {
    return (
        <div className="modal-overlay">
            <div className={`modal-content confirmation-modal ${isWarning ? 'warning-border' : ''}`}>
                <div className="modal-header">
                    <h3>{title}</h3>
                </div>
                <div className="modal-body">
                    <p>{message}</p>
                </div>
                <div className="modal-actions">
                    <button className="cancel-btn" onClick={onCancel}>
                        {cancelText}
                    </button>
                    {thirdOptionText && onThirdOption && (
                        <button className="confirm-btn secondary-btn" onClick={onThirdOption}>
                            {thirdOptionText}
                        </button>
                    )}
                    <button className={`confirm-btn ${isWarning ? 'warning-btn' : 'primary-btn'}`} onClick={onConfirm}>
                        {confirmText}
                    </button>
                </div>
            </div>
        </div>
    );
}
