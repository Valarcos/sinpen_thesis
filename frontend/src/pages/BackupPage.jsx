import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import api from '../services/api';
import './BackupPage.css'; // We will create this

export default function BackupPage() {
    const [backups, setBackups] = useState([]);
    const [groupedBackups, setGroupedBackups] = useState([]);
    const [loading, setLoading] = useState(true);
    const [userRole, setUserRole] = useState('');

    // Reboot UX State
    const [isRebooting, setIsRebooting] = useState(false);
    const [isCreating, setIsCreating] = useState(false);
    const [rebootMessage, setRebootMessage] = useState('');
    const [errorModalMessage, setErrorModalMessage] = useState('');

    // Modal State
    const [selectedBackup, setSelectedBackup] = useState(null);
    const [uploadFile, setUploadFile] = useState(null);
    const [showRestoreWarning, setShowRestoreWarning] = useState(false);
    const [showRestoreConfirm, setShowRestoreConfirm] = useState(false);
    const [restoreConfirmationInput, setRestoreConfirmationInput] = useState('');
    const isMounted = useRef(true);

    const navigate = useNavigate();

    useEffect(() => {
        isMounted.current = true;
        setUserRole(localStorage.getItem('userRole') || '');
        fetchBackups();
        return () => { isMounted.current = false; };
    }, []);

    const fetchBackups = async () => {
        try {
            if (isMounted.current) setLoading(true);
            const response = await api.get('/backups');
            if (!isMounted.current) return;
            setBackups(response.data);
            processGrouping(response.data);
        } catch (error) {
            console.error('Error fetching backups:', error);
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setLoading(false);
        }
    };

    const processGrouping = (backupList) => {
        const groups = {};

        backupList.forEach(file => {
            // Filename format: centralizesys_daily_20231025_1430.sql
            // We want to group by the timestamp part: 20231025_1430
            // Regex to extract timestamp: _(\d{8}_\d{4})\.
            const match = file.filename.match(/_(\d{8}_\d{4})\./);
            const timestamp = match ? match[1] : 'unknown';
            const type = file.type.includes('DAILY') ? 'Automático' : 'Manual';

            const key = `${type}_${timestamp}`;

            if (!groups[key]) {
                groups[key] = {
                    id: key,
                    date: file.date,
                    dateFormatted: new Date(file.date).toLocaleDateString(),
                    timeFormatted: new Date(file.date).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
                    typeDisplay: type,
                    sqlFile: null,
                    excelFile: null
                };
            }

            if (file.filename.endsWith('.sql')) {
                groups[key].sqlFile = file.filename;
            } else if (file.filename.endsWith('.xlsx')) {
                groups[key].excelFile = file.filename;
            }
        });

        // Convert to array and sort by date desc
        const sortedGroups = Object.values(groups).sort((a, b) => new Date(b.date) - new Date(a.date));
        if (isMounted.current) setGroupedBackups(sortedGroups);
    };

    // ... (handleCreateBackup remains same) ...


    const handleCreateBackup = async () => {
        if (isCreating) return;
        try {
            setIsCreating(true);
            await api.post('/backups/create');
            toast.success('Respaldo creado correctamente');
            fetchBackups();
        } catch (error) {
            console.error('Error creating backup:', error);
            // Error handled by global api interceptor
            // Vector 2: Force re-fetch on failure to resync state
            fetchBackups();
        } finally {
            if (isMounted.current) setIsCreating(false);
        }
    };

    const handleDownload = async (filename) => {
        try {
            const response = await api.get(`/backups/download/${filename}`, {
                responseType: 'blob'
            });

            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', filename);
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch (error) {
            console.error('Error downloading backup:', error);
            toast.error('Error al descargar archivo');
        }
    };

    const handleDownloadBoth = async (sqlFile, excelFile) => {
        toast.promise(
            Promise.all([
                handleDownload(sqlFile),
                new Promise(resolve => setTimeout(() => resolve(handleDownload(excelFile)), 1000)) // Small delay to prevent browser block
            ]),
            {
                loading: 'Iniciando descarga de ambos archivos...',
                success: 'Descargas iniciadas',
                error: 'Error al descargar algunos archivos'
            }
        );
    };

    // --- Restore Logic ---

    const initiateRestore = (backupFilename) => {
        setSelectedBackup(backupFilename);
        setUploadFile(null); // Ensure we are not in upload mode
        setShowRestoreWarning(true);
    };

    const initiateUploadRestore = (file) => {
        if (!file) return;
        if (!file.name.endsWith('.sql')) {
            toast.error('Solo se permiten archivos .sql');
            return;
        }
        setUploadFile(file);
        setSelectedBackup(file.name); // Just for display
        setShowRestoreWarning(true);
    };

    const handleWarningContinue = () => {
        setShowRestoreWarning(false);
        setShowRestoreConfirm(true);
        setRestoreConfirmationInput('');
    };

    const handleRestoreExecute = async () => {
        if (restoreConfirmationInput !== 'RESTAURAR') {
            toast.error('Debe escribir RESTAURAR para confirmar');
            return;
        }

        setShowRestoreConfirm(false);
        const loadingToast = toast.loading('Iniciando restauración...');

        try {
            let requestPromise;
            if (uploadFile) {
                const formData = new FormData();
                formData.append('file', uploadFile);
                requestPromise = api.post('/backups/upload-restore', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' },
                    timeout: 0 // Infinite timeout for long restore processes
                });
            } else {
                requestPromise = api.post(`/backups/restore/${selectedBackup}`, null, { timeout: 0 });
            }

            await requestPromise;

            toast.dismiss(loadingToast);
            setRebootMessage('Reiniciando Sistema...');
            setIsRebooting(true);

            setTimeout(() => {
                localStorage.clear();
                window.location.href = '/login';
            }, 20000);

        } catch (error) {
            console.error('Error restoring database:', error);
            toast.dismiss(loadingToast);

            let errMsg = 'Error al restaurar base de datos';
            if (error.response && error.response.data) {
                errMsg = typeof error.response.data === 'string'
                    ? error.response.data
                    : (error.response.data.message || error.message);
            } else if (error.message) {
                errMsg = error.message;
            }

            // Si el servidor rechaza activamente por un error de cliente (Ej. 400 Archivo vacío),
            // NO se está reiniciando, así que mostramos el error de inmediato.
            if (error.response && error.response.status >= 400 && error.response.status < 500) {
                setErrorModalMessage(errMsg);
                return;
            }

            // Si es 500 o error de red, asumimos que el servidor ejecutó el restore y se reinició (halt).
            setRebootMessage('Reiniciando para recuperarse...');
            setIsRebooting(true);

            setTimeout(() => {
                setIsRebooting(false);
                setErrorModalMessage(errMsg);
            }, 20000);
        }
    };

    // --- File Input Helper ---
    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            initiateUploadRestore(e.target.files[0]);
        }
    };



    return (
        <div className="backup-page container">
            <header className="page-header">
                <h1>💾 Gestión de Respaldos</h1>
                <div className="header-actions">
                    {userRole === 'ADMIN' && (
                        <>
                            <input
                                type="file"
                                id="upload-backup"
                                accept=".sql"
                                style={{ display: 'none' }}
                                onChange={handleFileChange}
                            />
                            <label htmlFor="upload-backup" className="button secondary">
                                📤 Subir Respaldo
                            </label>
                        </>
                    )}
                    <button onClick={handleCreateBackup} className="button primary" disabled={isCreating}>
                        {isCreating ? 'Creando...' : '+ Nuevo Respaldo'}
                    </button>
                    <button onClick={() => navigate('/')} className="button tertiary">
                        Volver
                    </button>
                </div>
            </header>

            <div className="backups-list-container">
                {loading ? (
                    <p>Cargando respaldos...</p>
                ) : groupedBackups.length === 0 ? (
                    <div className="empty-state">No hay respaldos disponibles.</div>
                ) : (
                    <table className="backups-table">
                        <thead>
                        <tr>
                            <th>Fecha</th>
                            <th>Tipo</th>
                            <th>Descargas Disponibles</th>
                            {userRole === 'ADMIN' && <th>Admin</th>}
                        </tr>
                        </thead>
                        <tbody>
                        {(groupedBackups || []).map((group) => (
                            <tr key={group.id}>
                                <td data-label="Fecha">
                                    <strong>{group.dateFormatted}</strong>
                                    <div style={{ fontSize: '0.9em', color: '#666' }}>
                                        {group.timeFormatted}
                                    </div>
                                </td>
                                <td data-label="Tipo">
                                    {group.typeDisplay}
                                </td>
                                <td data-label="Descargas">
                                    <div className="download-buttons-group">
                                        {/* Download Both */}
                                        {group.sqlFile && group.excelFile && (
                                            <button
                                                onClick={() => handleDownloadBoth(group.sqlFile, group.excelFile)}
                                                className="btn-large btn-download-all"
                                                title="Descargar ambos archivos"
                                            >
                                                ⬇️ DESCARGAR TODO
                                            </button>
                                        )}

                                        {/* Excel Button */}
                                        {group.excelFile && (
                                            <button
                                                onClick={() => handleDownload(group.excelFile)}
                                                className="btn-large btn-excel"
                                                title="Descargar Excel"
                                            >
                                                📊 EXCEL
                                            </button>
                                        )}

                                        {/* SQL Button */}
                                        {group.sqlFile && (
                                            <button
                                                onClick={() => handleDownload(group.sqlFile)}
                                                className="btn-large btn-db"
                                                title="Descargar Copia SQL"
                                            >
                                                🗄️ SQL
                                            </button>
                                        )}
                                    </div>
                                </td>
                                {userRole === 'ADMIN' && (
                                    <td data-label="Admin">
                                        {group.sqlFile && (
                                            <button
                                                onClick={() => initiateRestore(group.sqlFile)}
                                                className="btn-large btn-restore"
                                                title="Restaurar sistema a este punto"
                                            >
                                                🔄 Restaurar
                                            </button>
                                        )}
                                    </td>
                                )}
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Warning Modal */}
            {showRestoreWarning && (
                <div className="modal-overlay">
                    <div className="modal-content warning">
                        <h2>⚠️ Advertencia Crítica</h2>
                        <p>
                            Está a punto de restaurar la base de datos desde: <strong>{selectedBackup}</strong>
                        </p>
                        <p className="highlight-danger">
                            {uploadFile
                                ? "Esto reemplazará la base de datos actual con el archivo subido."
                                : "Esto borrará los datos actuales y los reemplazará con esta copia de seguridad."}
                        </p>
                        <p>Esta acción <strong>NO</strong> se puede deshacer.</p>

                        <div className="modal-actions">
                            <button onClick={() => setShowRestoreWarning(false)} className="button secondary">
                                Cancelar
                            </button>
                            <button onClick={handleWarningContinue} className="button danger">
                                Continuar
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Confirmation Modal */}
            {showRestoreConfirm && (
                <div className="modal-overlay">
                    <div className="modal-content danger">
                        <h2>🔒 Confirmación de Seguridad</h2>
                        <p>
                            ¿Está seguro que desea restaurar desde <strong>{selectedBackup}</strong>?
                        </p>
                        <p>
                            Para confirmar, escriba <strong>RESTAURAR</strong> en el campo de abajo:
                        </p>
                        <input
                            type="text"
                            value={restoreConfirmationInput}
                            onChange={(e) => setRestoreConfirmationInput(e.target.value)}
                            placeholder="RESTAURAR"
                            className="confirm-input"
                        />
                        <div className="modal-actions">
                            <button onClick={() => setShowRestoreConfirm(false)} className="button secondary">
                                Cancelar
                            </button>
                            <button
                                onClick={handleRestoreExecute}
                                className="button danger"
                                disabled={restoreConfirmationInput !== 'RESTAURAR'}
                            >
                                EJECUTAR RESTAURACIÓN
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Rebooting Overlay */}
            {isRebooting && (
                <div className="modal-overlay" style={{ zIndex: 9999 }}>
                    <div className="modal-content" style={{ textAlign: 'center' }}>
                        <h2>⏳ {rebootMessage}</h2>
                        <p>Por favor espere. El servidor se está reiniciando y aplicando los cambios. No cierre esta ventana.</p>
                        <div className="spinner" style={{ margin: '20px auto', width: '40px', height: '40px', border: '4px solid #f3f3f3', borderTop: '4px solid #3498db', borderRadius: '50%', animation: 'spin 1s linear infinite' }}></div>
                        <style>{`
                            @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
                        `}</style>
                    </div>
                </div>
            )}

            {/* Persistent Error Modal */}
            {errorModalMessage && (
                <div className="modal-overlay" style={{ zIndex: 10000 }}>
                    <div className="modal-content danger">
                        <h2>❌ Error Crítico en Restauración</h2>
                        <p>No se pudo completar la restauración. El sistema se reinició para recuperarse.</p>
                        <p><strong>Detalles:</strong> {errorModalMessage}</p>
                        <div className="modal-actions">
                            <button onClick={() => setErrorModalMessage('')} className="button secondary">
                                Cerrar y Volver a Intentar
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
