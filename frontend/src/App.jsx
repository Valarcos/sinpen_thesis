import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import BackupPage from './pages/BackupPage';
import AdminPage from './pages/AdminPage';
import SalesHistoryPage from './pages/SalesHistoryPage';
import ReportesPage from './pages/ReportesPage';
import CobrosYPedidosPage from './pages/CobrosYPedidosPage';
import VentaPage from './pages/VentaPage';
import InventarioPage from './pages/InventarioPage';
import ErrorBoundaryPage from './pages/ErrorBoundaryPage';

// ...


import AppLayout from './layouts/AppLayout';

/**
 * Protects routes from unauthenticated access.
 * Redirects to login if no JWT token is present.
 */
function ProtectedRoute({ children }) {
    const token = localStorage.getItem('jwt');

    if (!token) {
        return <Navigate to="/login" replace />;
    }

    return children;
}

/**
 * Protects admin-only routes.
 * Redirects to dashboard if user is not ADMIN.
 */
function AdminRoute({ children }) {
    const role = localStorage.getItem('userRole');
    return role === 'ADMIN' ? children : <Navigate to="/dashboard" replace />;
}

/**
 * Placeholder component for pages not yet implemented.
 */
function PlaceholderPage({ title, sprint = 3 }) {
    return (
        <div className="container">
            <h1>{title}</h1>
            <p>Próximamente en Sprint {sprint}</p>
        </div>
    );
}

// Data Router — required for useBlocker support
const router = createBrowserRouter([
    {
        path: '/login',
        element: <LoginPage />,
    },
    {
        path: '/',
        errorElement: <ErrorBoundaryPage />,
        element: (
            <ProtectedRoute>
                <AppLayout />
            </ProtectedRoute>
        ),
        children: [
            { index: true, element: <Navigate to="/dashboard" replace /> },
            { path: 'dashboard', element: <DashboardPage /> },
            { path: 'backups', element: <BackupPage /> },
            { path: 'ventas', element: <VentaPage /> },
            { path: 'inventario', element: <InventarioPage /> },
            { path: 'cobros-y-pedidos', element: <CobrosYPedidosPage /> },
            { path: 'reportes', element: <ReportesPage /> },
            { path: 'historial-ventas', element: <SalesHistoryPage /> },
            {
                path: 'admin',
                element: (
                    <AdminRoute>
                        <AdminPage />
                    </AdminRoute>
                ),
            },
        ],
    },
    {
        path: '*',
        element: <Navigate to="/" replace />,
    },
]);

function App() {
    return (
        <>
            <Toaster
                position="top-center"
                toastOptions={{
                    duration: 4000,
                    style: {
                        fontSize: '1.1rem',
                        padding: '16px',
                        maxWidth: '500px',
                    },
                    success: {
                        iconTheme: {
                            primary: 'var(--color-success)',
                            secondary: 'white',
                        },
                    },
                    error: {
                        duration: 8000,
                        iconTheme: {
                            primary: 'var(--color-error)',
                            secondary: 'white',
                        },
                    },
                }}
            />

            <RouterProvider router={router} />
        </>
    );
}

export default App;

