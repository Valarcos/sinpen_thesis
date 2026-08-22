import { useState, useEffect, useRef } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { blockNonNumericKeys, blockNonIntegerKeys, sanitizeNumericPaste, sanitizeIntegerPaste, enforceMoneyFormat } from '../utils/numericInput';
import './ProductFormModal.css';

/**
 * Modal for creating/editing products.
 * For new products, includes location selection and quantity input.
 * @param {boolean} isPurchaseContext - If true, hides initial stock fields (prevents double entry)
 */
export default function ProductFormModal({ product, isVariant = false, isPurchaseContext = false, initialCost, onSuccess, onCancel }) {
    const isEditing = !!product && !isVariant;
    const firstInputRef = useRef(null);
    const isMounted = useRef(true);

    // Ubicaciones (locations) for dropdown
    const [ubicaciones, setUbicaciones] = useState([]);
    const [loadingUbicaciones, setLoadingUbicaciones] = useState(true);

    // Form state
    const [formData, setFormData] = useState({
        codigo: product?.codigo || '',
        descripcion: product?.descripcion || '',
        precioCosto: initialCost !== undefined ? initialCost : (isVariant ? '' : (product?.precioCosto || '')),
        precioMayorista: product?.precioMayorista || '',
        precioMinorista: product?.precioMinorista || '',
        ubicacionId: '',
        cantidad: ''
    });

    const [errors, setErrors] = useState({});
    const [saving, setSaving] = useState(false);

    // Smart Form state (create mode only)
    const [isLoadingCode, setIsLoadingCode] = useState(false);
    const [isExistingFamily, setIsExistingFamily] = useState(false);
    const [codeEvaluated, setCodeEvaluated] = useState(isEditing); // editing = already evaluated
    // Smart lookup is only active when creating a brand-new product (not editing, not variant mode)
    const isSmartLookupEnabled = !isEditing && !isVariant;

    // Load ubicaciones on mount (only for new products AND if NOT in purchase context)
    useEffect(() => {
        isMounted.current = true;
        if (!isEditing && !isPurchaseContext) {
            loadUbicaciones();
        } else {
            if (isMounted.current) setLoadingUbicaciones(false);
        }
        return () => { isMounted.current = false; };
    }, [isEditing, isPurchaseContext]);

    // Focus first input on mount
    useEffect(() => {
        firstInputRef.current?.focus();
    }, []);

    const loadUbicaciones = async () => {
        try {
            const response = await api.get('/locations');
            if (!isMounted.current) return;
            setUbicaciones(response.data);
            // Auto-select first location if only one exists
            if (response.data.length === 1) {
                setFormData(prev => ({ ...prev, ubicacionId: response.data[0].id.toString() }));
            }
        } catch (error) {
            console.error('Error loading ubicaciones:', error);
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setLoadingUbicaciones(false);
        }
    };

    /**
     * Smart Form: Called when the user tabs away from the codigo field in create mode.
     * Queries the family endpoint to auto-fill description and prices for existing families.
     * Generic code "1" is skipped — it is a shared bucket and needs manual entry.
     */
    const handleCodeBlur = async () => {
        const code = formData.codigo.trim();
        if (!code || code === '1') {
            setCodeEvaluated(true);
            setIsExistingFamily(false);
            return;
        }

        setIsLoadingCode(true);
        try {
            const res = await api.get(`/productos/familia/${encodeURIComponent(code)}`);
            const family = res.data;
            if (!isMounted.current) return;

            if (family && family.length > 0) {
                const newest = family[family.length - 1];
                setFormData(prev => ({
                    ...prev,
                    descripcion: newest.descripcion,
                    precioMinorista: newest.precioMinorista?.toString() || '',
                    precioMayorista: newest.precioMayorista?.toString() || '',
                }));
                setIsExistingFamily(true);
            } else {
                setIsExistingFamily(false);
            }
            setCodeEvaluated(true);
        } catch (err) {
            console.error('Error looking up product family:', err);
            // Error handled by global api interceptor
            if (isMounted.current) {
                setCodeEvaluated(true);
                setIsExistingFamily(false);
            }
        } finally {
            if (isMounted.current) setIsLoadingCode(false);
        }
    };


    const handleChange = (e) => {
        const { name, value } = e.target;
        // Enforce 2-decimal limit on money/price fields
        const moneyFields = ['precioCosto', 'precioMayorista', 'precioMinorista'];
        const sanitized = moneyFields.includes(name) ? enforceMoneyFormat(value) : value;
        setFormData(prev => ({ ...prev, [name]: sanitized }));

        // Clear error when field is edited
        if (errors[name]) {
            setErrors(prev => ({ ...prev, [name]: null }));
        }
    };

    const validate = () => {
        const newErrors = {};

        // Codigo is required
        if (!formData.codigo.trim()) {
            newErrors.codigo = 'Código de artículo obligatorio. Usar "1" para productos sin código.';
        }

        // Descripcion is required
        if (!formData.descripcion.trim()) {
            newErrors.descripcion = 'Descripción obligatoria.';
        }

        // Precio Costo is required and >= 0
        const costo = parseFloat(formData.precioCosto);
        if (isNaN(costo) || costo < 0) {
            newErrors.precioCosto = 'Costo debe ser mayor o igual a 0.';
        }

        // Precio Minorista is required and >= costo
        const minorista = parseFloat(formData.precioMinorista);
        if (isNaN(minorista) || minorista < 0) {
            newErrors.precioMinorista = 'Precio minorista debe ser mayor o igual a 0.';
        } else if (!isNaN(costo) && minorista < costo) {
            newErrors.precioMinorista = 'Precio minorista debe ser mayor o igual al costo.';
        }

        // Precio Mayorista (optional) - if provided, >= costo
        if (formData.precioMayorista) {
            const mayorista = parseFloat(formData.precioMayorista);
            if (isNaN(mayorista) || mayorista < 0) {
                newErrors.precioMayorista = 'Precio mayorista debe ser mayor o igual a 0.';
            } else if (!isNaN(costo) && mayorista < costo) {
                newErrors.precioMayorista = 'Precio mayorista debe ser mayor o igual al costo.';
            }
        }

        // For new products: ubicacion and cantidad are required ONLY if NOT in purchase context
        if (!isEditing && !isPurchaseContext) {
            if (!formData.ubicacionId) {
                newErrors.ubicacionId = 'Debe seleccionar una ubicación.';
            }

            const cantidad = parseInt(formData.cantidad);
            if (isNaN(cantidad) || cantidad < 1) {
                newErrors.cantidad = 'Cantidad debe ser al menos 1.';
            }
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (saving) return;
        if (!validate()) {
            toast.error('Por favor, corrija los errores del formulario.');
            return;
        }

        setSaving(true);

        try {
            const payload = {
                codigo: formData.codigo.trim(),
                descripcion: formData.descripcion.trim(),
                precioCosto: parseFloat(formData.precioCosto),
                precioMayorista: formData.precioMayorista ? parseFloat(formData.precioMayorista) : null,
                precioMinorista: parseFloat(formData.precioMinorista)
            };

            // Add stock info for new products ONLY if NOT in purchase context
            if (!isEditing && !isPurchaseContext && formData.ubicacionId && formData.cantidad) {
                payload.ubicacionId = parseInt(formData.ubicacionId);
                payload.cantidad = parseInt(formData.cantidad);
            }

            let savedProduct;
            if (isEditing) {
                const res = await api.put(`/productos/${product.id}`, payload);
                savedProduct = res.data;
                toast.success('Producto actualizado correctamente');
            } else {
                const res = await api.post('/productos', payload);
                savedProduct = res.data;
                toast.success('Producto creado correctamente');
            }

            if (isMounted.current) onSuccess(savedProduct);
        } catch (error) {
            console.error('Error saving product:', error);
            // The global api.js interceptor automatically displays toast.error for API rejections
        } finally {
            if (isMounted.current) setSaving(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onCancel} role="dialog" aria-modal="true">
            <div className="product-form-modal" onClick={e => e.stopPropagation()}>
                <form onSubmit={handleSubmit}>
                    <div className="modal-header">
                        <h2>
                            {isEditing ? 'Editar Producto' :
                                (isVariant ? 'Nueva Variante' :
                                    (isPurchaseContext ? 'Nuevo Producto (Para Compra)' : 'Nuevo Producto'))}
                        </h2>
                    </div>

                    <div className="form-body">
                        {/* Codigo */}
                        <div className="form-group">
                            <label htmlFor="codigo">
                                Código de Artículo <span className="required">*</span>
                            </label>
                            <input
                                ref={firstInputRef}
                                id="codigo"
                                name="codigo"
                                type="text"
                                value={formData.codigo}
                                onChange={(e) => {
                                    handleChange(e);
                                    if (isSmartLookupEnabled) {
                                        setCodeEvaluated(false);
                                        setIsExistingFamily(false);
                                    }
                                }}
                                onBlur={isSmartLookupEnabled ? handleCodeBlur : undefined}
                                disabled={isVariant}
                                placeholder={isVariant ? 'Código pre-asignado' : 'Ej: ART-001'}
                                aria-invalid={!!errors.codigo}
                                tabIndex="1"
                            />
                            {errors.codigo && (
                                <span className="error-message">{errors.codigo}</span>
                            )}
                            {isLoadingCode && (
                                <span className="field-status-hint">Verificando código...</span>
                            )}
                            {!isLoadingCode && isExistingFamily && isSmartLookupEnabled && (
                                <span className="field-status-hint">Familia existente — descripción y precios autocompletados.</span>
                            )}
                        </div>

                        {/* Descripcion */}
                        <div className="form-group">
                            <label htmlFor="descripcion">
                                Descripción <span className="required">*</span>
                            </label>
                            <input
                                id="descripcion"
                                name="descripcion"
                                type="text"
                                value={formData.descripcion}
                                onChange={handleChange}
                                disabled={isLoadingCode || (isExistingFamily && isSmartLookupEnabled)}
                                placeholder="Nombre del producto"
                                aria-invalid={!!errors.descripcion}
                                tabIndex="2"
                            />
                            {errors.descripcion && (
                                <span className="error-message">{errors.descripcion}</span>
                            )}
                        </div>

                        {/* Precios Row */}
                        <div className="form-row prices-row">
                            <div className="form-group">
                                <label htmlFor="precioCosto">
                                    Costo <span className="required">*</span>
                                </label>
                                <input
                                    id="precioCosto"
                                    name="precioCosto"
                                    type="text"
                                    inputMode="decimal"
                                    min="0"
                                    value={formData.precioCosto}
                                    onChange={handleChange}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                    disabled={isLoadingCode || (!codeEvaluated && isSmartLookupEnabled)}
                                    placeholder="0.00"
                                    aria-invalid={!!errors.precioCosto}
                                    tabIndex="3"
                                />
                                {errors.precioCosto && (
                                    <span className="error-message">{errors.precioCosto}</span>
                                )}
                            </div>

                            <div className="form-group">
                                <label htmlFor="precioMayorista">
                                    Precio Mayorista
                                </label>
                                <input
                                    id="precioMayorista"
                                    name="precioMayorista"
                                    type="text"
                                    inputMode="decimal"
                                    min="0"
                                    value={formData.precioMayorista}
                                    onChange={handleChange}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                    disabled={isLoadingCode || isVariant || (isExistingFamily && isSmartLookupEnabled)}
                                    placeholder="Igual al minorista si vacío"
                                    tabIndex="4"
                                />
                                {errors.precioMayorista && (
                                    <span className="error-message">{errors.precioMayorista}</span>
                                )}
                            </div>

                            <div className="form-group">
                                <label htmlFor="precioMinorista">
                                    Precio Minorista <span className="required">*</span>
                                </label>
                                <input
                                    id="precioMinorista"
                                    name="precioMinorista"
                                    type="text"
                                    inputMode="decimal"
                                    min="0"
                                    value={formData.precioMinorista}
                                    onChange={handleChange}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                    disabled={isLoadingCode || isVariant || (isExistingFamily && isSmartLookupEnabled)}
                                    placeholder="0.00"
                                    aria-invalid={!!errors.precioMinorista}
                                    tabIndex="5"
                                />
                                {errors.precioMinorista && (
                                    <span className="error-message">{errors.precioMinorista}</span>
                                )}
                            </div>
                        </div>

                        {/* Stock Section - Only for new products AND NOT in purchase context */}
                        {!isEditing && !isPurchaseContext && (
                            <div className="stock-section">
                                <h3>📍 Ubicación del Stock Inicial</h3>

                                <div className="form-row stock-row">
                                    <div className="form-group">
                                        <label htmlFor="ubicacionId">
                                            Ubicación <span className="required">*</span>
                                        </label>
                                        {loadingUbicaciones ? (
                                            <p className="loading-text">Cargando ubicaciones...</p>
                                        ) : ubicaciones.length === 0 ? (
                                            <p className="warning-text">
                                                No hay ubicaciones registradas.
                                                Contacte al administrador para agregar ubicaciones.
                                            </p>
                                        ) : (
                                            <select
                                                id="ubicacionId"
                                                name="ubicacionId"
                                                value={formData.ubicacionId}
                                                onChange={handleChange}
                                                aria-invalid={!!errors.ubicacionId}
                                                tabIndex="6"
                                            >
                                                <option value="">-- Seleccione ubicación --</option>
                                                {(ubicaciones || []).filter(u => u.activo !== false).map(ub => (
                                                    <option key={ub.id} value={ub.id}>
                                                        {ub.nombre}
                                                    </option>
                                                ))}
                                            </select>
                                        )}
                                        {errors.ubicacionId && (
                                            <span className="error-message">{errors.ubicacionId}</span>
                                        )}
                                    </div>

                                    <div className="form-group">
                                        <label htmlFor="cantidad">
                                            Cantidad <span className="required">*</span>
                                        </label>
                                        <input
                                            id="cantidad"
                                            name="cantidad"
                                            type="text"
                                            inputMode="numeric"
                                            min="1"
                                            value={formData.cantidad}
                                            onChange={handleChange}
                                            onKeyDown={blockNonIntegerKeys}
                                            onPaste={sanitizeIntegerPaste}
                                            placeholder="Ingrese cantidad"
                                            aria-invalid={!!errors.cantidad}
                                            tabIndex="7"
                                        />
                                        {errors.cantidad && (
                                            <span className="error-message">{errors.cantidad}</span>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="form-actions">
                        <button
                            type="button"
                            onClick={onCancel}
                            className="secondary"
                            disabled={saving}
                            tabIndex="8"
                        >
                            Cancelar
                        </button>
                        <button
                            type="submit"
                            className="primary"
                            disabled={saving
                                || isLoadingCode
                                || (isSmartLookupEnabled && !codeEvaluated)
                                || (loadingUbicaciones && !isEditing && !isPurchaseContext)}
                            tabIndex="9"
                        >
                            {saving ? 'Guardando...' : (isEditing ? 'Actualizar' : 'Crear Producto')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
