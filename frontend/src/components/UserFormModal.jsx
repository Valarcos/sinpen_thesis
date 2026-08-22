import { useState, useEffect, useRef } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import './UserFormModal.css';

export default function UserFormModal({ user, onSuccess, onCancel }) {
    const isEditing = Boolean(user);
    const [formData, setFormData] = useState({
        nombre: '',
        email: '',
        password: '',
        rol: 'EMPLEADO'
    });
    const [loading, setLoading] = useState(false);
    const [errors, setErrors] = useState({});
    const nombreInputRef = useRef(null);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        if (user) {
            setFormData({
                nombre: user.nombre || '',
                email: user.email || '',
                password: '',
                rol: user.rol || 'EMPLEADO'
            });
        }
        // Focus on first input
        nombreInputRef.current?.focus();
        return () => { isMounted.current = false; };
    }, [user]);

    const validate = () => {
        const newErrors = {};

        if (!formData.nombre.trim()) {
            newErrors.nombre = 'El nombre es obligatorio';
        }

        if (!formData.email.trim()) {
            newErrors.email = 'El email es obligatorio';
        } else if (!formData.email.includes('@')) {
            newErrors.email = 'Ingrese un email válido';
        }

        if (!isEditing && !formData.password.trim()) {
            newErrors.password = 'La contraseña es obligatoria para nuevos usuarios';
        } else if (formData.password && formData.password.length < 6) {
            newErrors.password = 'La contraseña debe tener al menos 6 caracteres';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        // Clear error when user types
        if (errors[name]) {
            setErrors(prev => ({ ...prev, [name]: '' }));
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (loading) return;
        if (!validate()) return;

        setLoading(true);
        try {
            if (isEditing) {
                // Build update request with only changed fields
                const updateData = {};
                if (formData.nombre !== user.nombre) updateData.nombre = formData.nombre;
                if (formData.email !== user.email) updateData.email = formData.email;
                if (formData.password.trim()) updateData.password = formData.password;
                if (formData.rol !== user.rol) updateData.rol = formData.rol;

                await api.put(`/usuarios/${user.id}`, updateData);
                toast.success('Usuario actualizado correctamente');
            } else {
                await api.post('/usuarios/register', {
                    nombre: formData.nombre,
                    email: formData.email,
                    password: formData.password
                });
                toast.success('Usuario creado correctamente');
            }
            if (isMounted.current && onSuccess) onSuccess();
        } catch (error) {
            console.error('Error saving user:', error);
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setLoading(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onCancel}>
            <div className="user-form-modal" onClick={(e) => e.stopPropagation()}>
                <h2>{isEditing ? '✏️ Editar Usuario' : '➕ Crear Usuario'}</h2>

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="nombre">
                            Nombre <span className="required">*</span>
                        </label>
                        <input
                            ref={nombreInputRef}
                            id="nombre"
                            name="nombre"
                            type="text"
                            value={formData.nombre}
                            onChange={handleChange}
                            disabled={loading}
                            aria-invalid={errors.nombre ? 'true' : 'false'}
                            aria-describedby={errors.nombre ? 'nombre-error' : undefined}
                        />
                        {errors.nombre && (
                            <span id="nombre-error" className="error-message">{errors.nombre}</span>
                        )}
                    </div>

                    <div className="form-group">
                        <label htmlFor="email">
                            Email <span className="required">*</span>
                        </label>
                        <input
                            id="email"
                            name="email"
                            type="email"
                            value={formData.email}
                            onChange={handleChange}
                            disabled={loading}
                            aria-invalid={errors.email ? 'true' : 'false'}
                            aria-describedby={errors.email ? 'email-error' : undefined}
                        />
                        {errors.email && (
                            <span id="email-error" className="error-message">{errors.email}</span>
                        )}
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">
                            Contraseña {!isEditing && <span className="required">*</span>}
                        </label>
                        <input
                            id="password"
                            name="password"
                            type="password"
                            value={formData.password}
                            onChange={handleChange}
                            disabled={loading}
                            placeholder={isEditing ? 'Dejar vacío para mantener actual' : ''}
                            aria-invalid={errors.password ? 'true' : 'false'}
                            aria-describedby={errors.password ? 'password-error' : undefined}
                        />
                        {errors.password && (
                            <span id="password-error" className="error-message">{errors.password}</span>
                        )}
                        {isEditing && (
                            <span className="help-text">
                                Deje vacío si no desea cambiar la contraseña
                            </span>
                        )}
                    </div>

                    <div className="form-group">
                        <label htmlFor="rol">Rol</label>
                        <select
                            id="rol"
                            name="rol"
                            value={formData.rol}
                            onChange={handleChange}
                            disabled={loading || (user && user.id === 0)}
                        >
                            <option value="EMPLEADO">EMPLEADO</option>
                            <option value="ADMIN">ADMIN</option>
                        </select>
                        {user && user.id === 0 && (
                            <span className="help-text">
                                No se puede cambiar el rol del usuario del sistema
                            </span>
                        )}
                    </div>

                    <div className="modal-actions">
                        <button
                            type="button"
                            onClick={onCancel}
                            className="secondary"
                            disabled={loading}
                        >
                            Cancelar
                        </button>
                        <button
                            type="submit"
                            className="primary"
                            disabled={loading}
                        >
                            {loading ? 'Guardando...' : (isEditing ? 'Guardar Cambios' : 'Crear Usuario')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
