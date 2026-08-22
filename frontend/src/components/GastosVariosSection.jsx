import { useState, useEffect, useRef, useCallback } from 'react';
import api from '../services/api';
import { formatCurrency } from '../utils/format';
import toast from 'react-hot-toast';
import './GastosVariosSection.css';

const DEFAULT_FILTER_PARAMS = {};

const getLocalDatetimeLocal = () => {
    const d = new Date();
    const formatter = new Intl.DateTimeFormat('es-AR', {
        timeZone: 'America/Argentina/Buenos_Aires',
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', hour12: false
    });
    const parts = formatter.formatToParts(d);
    const p = {};
    parts.forEach(({type, value}) => p[type] = value);
    return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
};

const formatDisplayDate = (isoString) => {
    return new Date(isoString).toLocaleString('es-AR', {
        timeZone: 'America/Argentina/Buenos_Aires',
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', hour12: false
    });
};

export default function GastosVariosSection({ onGastosChanged, filterParams = DEFAULT_FILTER_PARAMS }) {
    const [gastos, setGastos] = useState([]);
    const [loading, setLoading] = useState(true);

    // Pagination state
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    const [size, setSize] = useState(window.innerWidth <= 768 ? 5 : 10);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        const handleResize = () => setSize(window.innerWidth <= 768 ? 5 : 10);
        window.addEventListener('resize', handleResize);
        return () => {
            isMounted.current = false;
            window.removeEventListener('resize', handleResize);
        };
    }, []);

    // Submitting states
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isCanceling, setIsCanceling] = useState(false);

    // Expanded texts state for "Ver más / Ver menos"
    const [expandedMotivos, setExpandedMotivos] = useState(new Set());

    // Form states
    const [monto, setMonto] = useState('');
    const [motivo, setMotivo] = useState('');
    const [fechaGasto, setFechaGasto] = useState(getLocalDatetimeLocal);
    const [categoria, setCategoria] = useState('Otros');
    const [persona, setPersona] = useState('');

    // Cancel modal states
    const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);
    const [cancelGasto, setCancelGasto] = useState(null);
    const [cancelReason, setCancelReason] = useState('');

    // Refs for focus management
    const montoRef = useRef(null);
    const motivoRef = useRef(null);
    const fechaRef = useRef(null);
    const categoriaRef = useRef(null);
    const personaRef = useRef(null);
    const submitBtnRef = useRef(null);

    const categorias = ['Servicios', 'Sueldos', 'Retiro Dueño', 'Otros'];

    const loadGastos = useCallback(async () => {
        setLoading(true);
        try {
            const res = await api.get('/gastos', {
                params: {
                    ...filterParams,
                    page,
                    size
                }
            });
            if (!isMounted.current) return;
            // Defensive parsing to support both new PageResponse and old Array formats if backend is not restarted
            const data = res.data;
            if (Array.isArray(data)) {
                setGastos(data);
                setTotalPages(1);
                setTotalElements(data.length);
            } else if (data && Array.isArray(data.content)) {
                setGastos(data.content);
                setTotalPages(data.totalPages || 1);
                setTotalElements(data.totalElements || data.content.length);
            } else {
                setGastos([]);
                setTotalPages(0);
                setTotalElements(0);
            }
        } catch (error) {
            console.error("Error loading gastos:", error);
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setLoading(false);
        }
    }, [filterParams, page]);

    useEffect(() => {
        loadGastos();
    }, [loadGastos]);

    // When filters change, reset to page 0
    useEffect(() => {
        setPage(0);
    }, [filterParams]);

    const handleKeyDown = (e, nextRef) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            nextRef.current?.focus();
        }
    };

    const handleMontoChange = (e) => {
        // Physical regex blocker: only numbers and dot allowed
        const val = e.target.value.replace(/[^0-9.]/g, '');
        setMonto(val);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (isSubmitting) return;

        if (!monto || isNaN(monto) || Number(monto) <= 0) {
            toast.error("El monto debe ser un número positivo");
            return;
        }
        if (!motivo.trim()) {
            toast.error("El motivo es obligatorio");
            return;
        }

        const payload = {
            monto: Number(monto),
            motivo: motivo.trim(),
            fechaGasto: fechaGasto ? new Date(fechaGasto + '-03:00').toISOString() : null,
            categoria,
            personaInvolucrada: persona.trim() || null
        };

        try {
            setIsSubmitting(true);
            await api.post('/gastos', payload);
            toast.success("Gasto registrado correctamente");

            if (isMounted.current) {
                // Reset form
                setMonto('');
                setMotivo('');
                setFechaGasto(getLocalDatetimeLocal());
                setCategoria('Otros');
                setPersona('');

                // Reload (force to page 0 to see the new entry)
                setPage(0);
            }
            loadGastos();
            if (onGastosChanged) onGastosChanged();

            // Focus back to start
            montoRef.current?.focus();
        } catch (error) {
            console.error("Error saving gasto:", error);
            // Vector 2: Force refetch on failure
            loadGastos();
        } finally {
            if (isMounted.current) setIsSubmitting(false);
        }
    };

    const openCancelModal = (gasto) => {
        setCancelGasto(gasto);
        setCancelReason('');
        setIsCancelModalOpen(true);
    };

    const handleCancelSubmit = async (e) => {
        e.preventDefault();

        if (isCanceling) return;

        try {
            setIsCanceling(true);
            await api.post(`/gastos/${cancelGasto.id}/anular`, { razonAnulacion: cancelReason });
            toast.success("Gasto anulado correctamente");
            if (isMounted.current) {
                setIsCancelModalOpen(false);
                setCancelGasto(null);
            }

            loadGastos();
            if (onGastosChanged) onGastosChanged();
        } catch (error) {
            console.error("Error canceling gasto:", error);
            // Vector 2: Force refetch on failure
            loadGastos();
        } finally {
            if (isMounted.current) setIsCanceling(false);
        }
    };

    const toggleMotivo = (id) => {
        setExpandedMotivos(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    // Helper for reason truncation
    const renderMotivo = (g) => {
        const text = g.motivo || '';
        const maxLength = 45;
        if (text.length <= maxLength) return text;

        const isExpanded = expandedMotivos.has(g.id);

        return (
            <>
                {isExpanded ? text : text.substring(0, maxLength - 3) + '...'}
                <button
                    type="button"
                    className="btn-link-small"
                    onClick={() => toggleMotivo(g.id)}
                    style={{ marginLeft: '5px', fontSize: '0.8rem', color: 'var(--color-primary)', background: 'none', border: 'none', cursor: 'pointer', textDecoration: 'underline' }}
                >
                    {isExpanded ? 'Ver menos' : 'Ver más'}
                </button>
            </>
        );
    };
    const getDynamicLabel = () => {
        if (!filterParams) return 'Historial Gastos';
        if (filterParams.day && filterParams.month && filterParams.year) {
            const dd = String(filterParams.day).padStart(2, '0');
            const mm = String(filterParams.month).padStart(2, '0');
            return `Historial Gastos (${dd}/${mm}/${filterParams.year})`;
        } else if (filterParams.month && filterParams.year) {
            const mm = String(filterParams.month).padStart(2, '0');
            return `Historial Gastos (${mm}/${filterParams.year})`;
        } else if (filterParams.year) {
            return `Historial Gastos (${filterParams.year})`;
        }
        return 'Historial Gastos';
    };

    return (
        <div className="report-section gastos">
            <h2 className="report-section-title">💸 GASTOS VARIOS y RETIROS</h2>

            <div className="gastos-container">
                <div className="gastos-form-card">
                    <h3>Registrar Nuevo Gasto / Retiro</h3>
                    <form onSubmit={handleSubmit} className="gastos-form">
                        <div className="form-group">
                            <label>Monto ($)*</label>
                            <input
                                type="text"
                                inputMode="decimal"
                                min="0"
                                value={monto}
                                onChange={handleMontoChange}
                                required
                                ref={montoRef}
                                onKeyDown={(e) => handleKeyDown(e, motivoRef)}
                                tabIndex={1}
                            />
                        </div>
                        <div className="form-group">
                            <label>Motivo*</label>
                            <input
                                type="text"
                                value={motivo}
                                onChange={(e) => setMotivo(e.target.value)}
                                required
                                placeholder="Ej: Pago de luz, Retiro personal..."
                                ref={motivoRef}
                                onKeyDown={(e) => handleKeyDown(e, fechaRef)}
                                tabIndex={2}
                            />
                        </div>
                        <div className="form-group">
                            <label>Fecha y Hora</label>
                            <input
                                type="datetime-local"
                                value={fechaGasto}
                                onChange={(e) => setFechaGasto(e.target.value)}
                                ref={fechaRef}
                                onKeyDown={(e) => handleKeyDown(e, categoriaRef)}
                                tabIndex={3}
                            />
                        </div>
                        <div className="form-group">
                            <label>Categoría</label>
                            <select
                                value={categoria}
                                onChange={(e) => setCategoria(e.target.value)}
                                ref={categoriaRef}
                                onKeyDown={(e) => handleKeyDown(e, personaRef)}
                                tabIndex={4}
                            >
                                {(categorias || []).map(c => <option key={c} value={c}>{c}</option>)}
                            </select>
                        </div>
                        <div className="form-group">
                            <label>Persona (Opcional)</label>
                            <input
                                type="text"
                                value={persona}
                                onChange={(e) => setPersona(e.target.value)}
                                placeholder="Por defecto: tu usuario"
                                ref={personaRef}
                                onKeyDown={(e) => handleKeyDown(e, submitBtnRef)}
                                tabIndex={5}
                            />
                        </div>

                        <button
                            type="submit"
                            className="btn-primary submit-gasto"
                            ref={submitBtnRef}
                            tabIndex={6}
                            disabled={isSubmitting}
                        >
                            {isSubmitting ? 'Registrando...' : 'Registrar'}
                        </button>
                    </form>
                </div>

                <div className="gastos-history-card">
                    <div className="gastos-history-header">
                        <h3>{getDynamicLabel()}</h3>

                        <div className="history-header-actions">
                            <span className="total-label">
                                Total: {totalElements} gastos
                            </span>

                            {totalPages > 0 && (
                                <div className="pagination-controls">
                                    <button
                                        className="btn-pagination"
                                        onClick={() => setPage(Math.max(0, page - 1))}
                                        disabled={page === 0}
                                    >
                                        Anterior
                                    </button>
                                    <span className="page-indicator">
                                        {page + 1} / {totalPages}
                                    </span>
                                    <button
                                        className="btn-pagination"
                                        onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                                        disabled={page >= totalPages - 1}
                                    >
                                        Siguiente
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>
                    {loading ? (
                        <p>Cargando gastos...</p>
                    ) : gastos.length === 0 ? (
                        <p className="empty-text">No hay gastos registrados en este período.</p>
                    ) : (
                        <div className="gastos-table-container">
                            <table className="gastos-table">
                                <thead>
                                <tr>
                                    <th className="fecha-col">Fecha</th>
                                    <th className="motivo-col">Motivo</th>
                                    <th>Categoría</th>
                                    <th>Monto</th>
                                    <th>Registró</th>
                                    <th className="estado-col">Estado</th>
                                    <th className="acciones-col">Acciones</th>
                                </tr>
                                </thead>
                                <tbody>
                                {(gastos || []).map(g => (
                                    <tr key={g.id} className={g.anulado ? 'row-canceled' : ''}>
                                        <td data-label="Fecha" className="fecha-col">{formatDisplayDate(g.fechaGasto)}</td>
                                        <td data-label="Motivo" className="motivo-col" title={g.motivo}>
                                            {renderMotivo(g)}
                                            {g.anulado && g.razonAnulacion && (
                                                <div className="razon-anulacion-text" title={g.razonAnulacion}>
                                                    Razón: {g.razonAnulacion}
                                                </div>
                                            )}
                                        </td>
                                        <td data-label="Categoría">{g.categoria}</td>
                                        <td data-label="Monto" className="monto-cell">-{formatCurrency(g.monto)}</td>
                                        <td data-label="Registró">{g.personaInvolucrada}</td>
                                        <td data-label="Estado">
                                            {g.anulado ? (
                                                <span className="badge-canceled">CANCELADO</span>
                                            ) : (
                                                <span className="badge-active">ACTIVO</span>
                                            )}
                                        </td>
                                        <td data-label="Acciones">
                                            {!g.anulado && (
                                                <button
                                                    className="btn-delete-small"
                                                    onClick={() => openCancelModal(g)}
                                                >
                                                    Anular
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>


                        </div>
                    )}
                </div>
            </div>

            {/* Cancelation Modal */}
            {isCancelModalOpen && cancelGasto && (
                <div className="modal-overlay">
                    <div className="modal-content cancel-modal">
                        <div className="modal-header modal-header-danger">
                            <h2>⚠️ Anular Gasto / Retiro</h2>
                        </div>
                        <div className="modal-body">
                            <p className="warning-text">
                                ¿Estás seguro que deseas anular este registro?<br/>
                                <strong>{formatDisplayDate(cancelGasto.fechaGasto)}</strong>, <strong>{cancelGasto.motivo}</strong>, <strong>${cancelGasto.monto.toLocaleString('es-AR', {minimumFractionDigits: 2, maximumFractionDigits: 2})}</strong><br/><br/>
                                El dinero volverá a figurar en el balance de la caja. Esta acción quedará registrada en Auditoría.
                            </p>

                            <form onSubmit={handleCancelSubmit}>
                                <div className="form-group cancel-reason">
                                    <label>Especifique razón de anulación de expensa:</label>
                                    <textarea
                                        value={cancelReason}
                                        onChange={(e) => setCancelReason(e.target.value)}
                                        placeholder="Opcional pero recomendado para auditoría..."
                                        rows="3"
                                        autoFocus
                                    />
                                </div>
                                <div className="modal-actions">
                                    <button type="button" className="btn-secondary" onClick={() => setIsCancelModalOpen(false)} disabled={isCanceling}>
                                        Volver
                                    </button>
                                    <button type="submit" className="btn-danger" disabled={isCanceling}>
                                        {isCanceling ? 'Anulando...' : 'Confirmar Anulación'}
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
