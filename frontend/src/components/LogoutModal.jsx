import './LogoutModal.css';

export default function LogoutModal({ isOpen, onConfirm, onCancel, isLoggingOut }) {
    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={isLoggingOut ? undefined : onCancel} role="dialog" aria-modal="true">
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <h2>¿Cerrar sesión?</h2>
                <p>¿Está seguro que desea salir del sistema?</p>

                <div className="modal-actions">
                    <button
                        className="secondary"
                        onClick={onCancel}
                        autoFocus
                        aria-label="Cancelar cierre de sesión"
                        disabled={isLoggingOut}
                    >
                        Cancelar
                    </button>
                    <button
                        className="primary"
                        onClick={onConfirm}
                        aria-label="Confirmar cierre de sesión"
                        disabled={isLoggingOut}
                    >
                        {isLoggingOut ? 'Saliendo...' : 'Salir'}
                    </button>
                </div>
            </div>
        </div>
    );
}
