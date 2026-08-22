import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import api from '../services/api';
import './LoginPage.css';

export default function LoginPage() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const isMounted = useRef(true);
    const navigate = useNavigate();

    // Display any error message that was persisted by the 401 interceptor
    // before it triggered a hard page redirect. This is the only mechanism
    // that survives a full page reload and guarantees the user sees the failure reason.
    useEffect(() => {
        isMounted.current = true;
        const pendingError = sessionStorage.getItem('pendingAuthError');
        if (pendingError) {
            sessionStorage.removeItem('pendingAuthError');
            toast.error(pendingError);
        }
        return () => { isMounted.current = false; };
    }, []);

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!email || !password) {
            toast.error('Complete todos los campos');
            return;
        }

        if (!email.includes('@')) {
            toast.error('Ingrese un email válido');
            return;
        }

        if (isMounted.current) setLoading(true);

        try {
            // IMPORTANT: baseURL is already '/api' (from .env.production).
            // The path here must NOT repeat the '/api' prefix, or Caddy will
            // route to '/api/api/auth/login', which Spring Security rejects as 401.
            const response = await api.post('/auth/login', { email, password });
            const { token, email: userEmail, nombre, rol } = response.data;

            localStorage.setItem('jwt', token);
            localStorage.setItem('userEmail', userEmail);
            localStorage.setItem('userName', nombre);
            localStorage.setItem('userRole', rol);

            // Clear stock alert dismissal so warning shows fresh on dashboard
            sessionStorage.removeItem('stockAlertDismissed');

            toast.success(`¡Bienvenido, ${nombre}!`);
            navigate('/dashboard');
        } catch (error) {
            console.error('Login error:', error);
        } finally {
            if (isMounted.current) setLoading(false);
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <h1>CentralizeSys</h1>
                <p className="subtitle">Sistema de Gestión</p>

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="email">Email</label>
                        <input
                            id="email"
                            type="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            autoComplete="email"
                            disabled={loading}
                            placeholder="usuario@ejemplo.com"
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">Contraseña</label>
                        <input
                            id="password"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            autoComplete="current-password"
                            disabled={loading}
                            placeholder="••••••••"
                        />
                    </div>

                    <button type="submit" className="primary" disabled={loading}>
                        {loading ? 'Ingresando...' : 'Ingresar'}
                    </button>
                </form>
            </div>
        </div>
    );
}
