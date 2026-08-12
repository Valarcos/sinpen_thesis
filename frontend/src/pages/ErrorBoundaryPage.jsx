import { useRouteError, useNavigate } from 'react-router-dom';
import './ErrorBoundaryPage.css';

export default function ErrorBoundaryPage() {
    const error = useRouteError();
    const navigate = useNavigate();

    console.error(error);

    return (
        <div className="error-boundary-container">
            <div className="error-boundary-content">
                <div className="error-icon">⚠️</div>
                <h1>¡Vaya! Algo salió mal.</h1>
                <p>Ha ocurrido un error inesperado en la aplicación.</p>
                <div className="error-details">
                    <pre>
                        {error?.statusText || error?.message || 'Error desconocido'}
                    </pre>
                </div>
                <div className="error-actions">
                    <button
                        className="btn-primary"
                        onClick={() => { window.location.href = '/'; }}
                    >
                        Volver al inicio
                    </button>
                    <button
                        className="btn-secondary"
                        onClick={() => window.location.reload()}
                    >
                        Recargar página
                    </button>
                </div>
            </div>
        </div>
    );
}
