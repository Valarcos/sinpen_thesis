import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useState, useEffect } from 'react';
import LogoutModal from '../components/LogoutModal';
import api from '../services/api';
import './AppLayout.css';

export default function AppLayout() {
    const [showLogoutModal, setShowLogoutModal] = useState(false);
    const [showMobileMenu, setShowMobileMenu] = useState(false);
    const [userName, setUserName] = useState('');
    const [userRole, setUserRole] = useState('');
    const [salesActiveTab, setSalesActiveTab] = useState('catalog');
    const navigate = useNavigate();
    const location = useLocation();
    const isVentasPage = location.pathname.startsWith('/ventas');
    const isFullWidthPage = location.pathname === '/cobros-y-pedidos' || location.pathname === '/historial-ventas';

    useEffect(() => {
        setUserName(localStorage.getItem('userName') || 'Usuario');
        setUserRole(localStorage.getItem('userRole') || 'EMPLEADO');
    }, [location.pathname]);

    const handleLogout = async () => {
        try {
            await api.post('/auth/logout');
        } catch (error) {
            console.error('Logout error:', error);
        } finally {
            localStorage.removeItem('jwt');
            localStorage.removeItem('userEmail');
            localStorage.removeItem('userName');
            localStorage.removeItem('userRole');
            navigate('/login');
        }
    };

    const isActive = (path) => location.pathname.startsWith(path);

    return (
        <div className="app-layout">
            {/* Desktop Top Navigation */}
            <nav className="top-nav desktop-only" aria-label="Navegación principal">
                <div className="container nav-content">
                    <h1 className="brand">CentralizeSys</h1>

                    <div className="nav-links">
                        <NavLink to="/dashboard" className={isActive('/dashboard') ? 'active' : ''}>
                            🏠 Inicio
                        </NavLink>
                        <NavLink to="/ventas" className={isActive('/ventas') ? 'active' : ''}>
                            💰 Ventas
                        </NavLink>
                        <NavLink to="/inventario" className={isActive('/inventario') ? 'active' : ''}>
                            📦 Inventario
                        </NavLink>
                        <NavLink to="/cobros-y-pedidos" className={isActive('/cobros-y-pedidos') ? 'active' : ''}>
                            💰 Pendientes
                        </NavLink>
                        <NavLink to="/historial-ventas" className={isActive('/historial-ventas') ? 'active' : ''}>
                            📖 Historial
                        </NavLink>
                        <NavLink to="/reportes" className={isActive('/reportes') ? 'active' : ''}>
                            📊 Reportes
                        </NavLink>
                        <NavLink to="/backups" className={isActive('/backups') ? 'active' : ''}>
                            💾 Respaldos
                        </NavLink>
                        {userRole === 'ADMIN' && (
                            <NavLink to="/admin" className={isActive('/admin') ? 'active' : ''}>
                                🔐 Admin
                            </NavLink>
                        )}
                    </div>

                    <div className="nav-user-section">
                        <span className="user-greeting">Hola, {userName}</span>
                        <button
                            onClick={() => setShowLogoutModal(true)}
                            className="logout-btn"
                            aria-label="Cerrar sesión"
                        >
                            Salir
                        </button>
                    </div>
                </div>
            </nav>

            {/* Mobile Compact Top Bar */}
            <nav className="top-nav-mobile mobile-only" aria-label="Navegación">
                <div className="mobile-nav-content">
                    <h1 className="brand-mobile">CentralizeSys</h1>
                    <button
                        onClick={() => setShowLogoutModal(true)}
                        className="logout-btn-mobile"
                        aria-label="Cerrar sesión"
                    >
                        Salir
                    </button>
                </div>
            </nav>

            {/* Main Content */}
            <main className={`main-content ${isFullWidthPage ? 'full-width-page' : ''}`}>
                <Outlet key={location.pathname} context={{ salesActiveTab, setSalesActiveTab }} />
            </main>

            {/* Mobile Bottom Navigation — Contextual for Sales page */}
            <nav className={`bottom-nav mobile-only ${isVentasPage ? 'sales-mode' : ''}`} aria-label="Navegación móvil">
                {isVentasPage ? (
                    /* Sales-specific tabs */
                    <>
                        <button
                            onClick={() => navigate('/dashboard')}
                            className="sales-tab-btn inicio"
                            aria-label="Volver a inicio"
                        >
                            <span className="icon" aria-hidden="true">🏠</span>
                            <span>Inicio</span>
                        </button>
                        <button
                            onClick={() => setSalesActiveTab('catalog')}
                            className={`sales-tab-btn ${salesActiveTab === 'catalog' ? 'active' : ''}`}
                            aria-label="Ver catálogo"
                        >
                            <span className="icon" aria-hidden="true">📋</span>
                            <span>Catálogo</span>
                        </button>
                        <button
                            onClick={() => setSalesActiveTab('ticket')}
                            className={`sales-tab-btn ${salesActiveTab === 'ticket' ? 'active' : ''}`}
                            aria-label="Ver ticket"
                        >
                            <span className="icon" aria-hidden="true">🧾</span>
                            <span>Ticket</span>
                        </button>
                    </>
                ) : (
                    /* Standard navigation */
                    <>
                        <button
                            onClick={() => navigate('/dashboard')}
                            className={isActive('/dashboard') ? 'active' : ''}
                            aria-label="Ir a inicio"
                            aria-current={isActive('/dashboard') ? 'page' : undefined}
                        >
                            <span className="icon" aria-hidden="true">🏠</span>
                            <span>Inicio</span>
                        </button>

                        <button
                            onClick={() => navigate('/ventas')}
                            className={isActive('/ventas') ? 'active' : ''}
                            aria-label="Ir a ventas"
                            aria-current={isActive('/ventas') ? 'page' : undefined}
                        >
                            <span className="icon" aria-hidden="true">💰</span>
                            <span>Ventas</span>
                        </button>

                        <button
                            onClick={() => navigate('/inventario')}
                            className={isActive('/inventario') ? 'active' : ''}
                            aria-label="Ir a inventario"
                            aria-current={isActive('/inventario') ? 'page' : undefined}
                        >
                            <span className="icon" aria-hidden="true">📦</span>
                            <span>Inventario</span>
                        </button>

                        <button
                            onClick={() => setShowMobileMenu(true)}
                            className={showMobileMenu ? 'active' : ''}
                            aria-label="Abrir menú"
                            aria-expanded={showMobileMenu}
                        >
                            <span className="icon" aria-hidden="true">⋮</span>
                            <span>Más</span>
                        </button>
                    </>
                )}
            </nav>

            {/* Mobile "Más" Menu (Overlay) */}
            {showMobileMenu && (
                <div
                    className="mobile-menu-overlay"
                    onClick={() => setShowMobileMenu(false)}
                    aria-label="Cerrar menú"
                >
                    <div
                        className="mobile-menu"
                        onClick={(e) => e.stopPropagation()}
                        role="menu"
                        aria-label="Menú adicional"
                    >
                        <h2>Menú</h2>
                        <NavLink
                            to="/cobros-y-pedidos"
                            onClick={() => setShowMobileMenu(false)}
                            className={isActive('/cobros-y-pedidos') ? 'active' : ''}
                        >
                            💳 Pendientes
                        </NavLink>
                        <NavLink to="/historial-ventas" className={isActive('/historial-ventas') ? 'active' : ''} onClick={() => setShowMobileMenu(false)}>
                            📖 Historial
                        </NavLink>
                        <NavLink to="/reportes" className={isActive('/reportes') ? 'active' : ''} onClick={() => setShowMobileMenu(false)}>
                            📊 Reportes
                        </NavLink>
                        <NavLink to="/backups" className={isActive('/backups') ? 'active' : ''} onClick={() => setShowMobileMenu(false)}>
                            💾 Respaldos
                        </NavLink>
                        {userRole === 'ADMIN' && (
                            <NavLink
                                to="/admin"
                                onClick={() => setShowMobileMenu(false)}
                                className={isActive('/admin') ? 'active' : ''}
                            >
                                🔐 Administración
                            </NavLink>
                        )}
                        <button
                            onClick={() => setShowMobileMenu(false)}
                            className="secondary"
                            aria-label="Cerrar menú"
                        >
                            Cerrar
                        </button>
                    </div>
                </div>
            )}

            <LogoutModal
                isOpen={showLogoutModal}
                onConfirm={handleLogout}
                onCancel={() => setShowLogoutModal(false)}
            />
        </div>
    );
}
