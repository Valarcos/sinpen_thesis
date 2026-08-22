import { useState, useEffect, useCallback, useRef } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import UserFormModal from '../components/UserFormModal';
import DeleteUserModal from '../components/DeleteUserModal';
import LocationManagementModal from '../components/LocationManagementModal';
import './AdminPage.css';

export default function AdminPage() {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showUserForm, setShowUserForm] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [showLocationModal, setShowLocationModal] = useState(false);
    const [editingUser, setEditingUser] = useState(null);
    const [deletingUser, setDeletingUser] = useState(null);
    const [isDeleting, setIsDeleting] = useState(false);
    const isMounted = useRef(true);

    const fetchUsers = useCallback(async () => {
        try {
            if (isMounted.current) setLoading(true);
            const response = await api.get('/usuarios');
            if (isMounted.current) setUsers(response.data);
        } catch (error) {
            console.error('Error fetching users:', error);
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setLoading(false);
        }
    }, []);

    useEffect(() => {
        isMounted.current = true;
        fetchUsers();
        return () => { isMounted.current = false; };
    }, [fetchUsers]);

    const handleCreateUser = () => {
        setEditingUser(null);
        setShowUserForm(true);
    };

    const handleEditUser = (user) => {
        setEditingUser(user);
        setShowUserForm(true);
    };

    const handleDeleteClick = (user) => {
        setDeletingUser(user);
        setShowDeleteModal(true);
    };

    const handleUserFormSuccess = () => {
        setShowUserForm(false);
        setEditingUser(null);
        fetchUsers();
    };

    const handleDeleteConfirm = async () => {
        if (!deletingUser || isDeleting) return;

        try {
            if (isMounted.current) setIsDeleting(true);
            await api.delete(`/usuarios/${deletingUser.id}`);
            toast.success('Usuario eliminado correctamente');
            if (isMounted.current) {
                setShowDeleteModal(false);
                setDeletingUser(null);
            }
            fetchUsers();
        } catch (error) {
            console.error('Error deleting user:', error);
            // Error handled by global api interceptor
            // Vector 2: Force re-fetch on failure to resync state
            fetchUsers();
        } finally {
            if (isMounted.current) setIsDeleting(false);
        }
    };

    const getRoleBadgeClass = (rol) => {
        return rol === 'ADMIN' ? 'role-badge admin' : 'role-badge empleado';
    };

    const currentUserEmail = localStorage.getItem('userEmail');

    return (
        <div className="admin-page container">
            <header className="admin-header">
                <h1>🔐 Administración</h1>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button
                        onClick={() => setShowLocationModal(true)}
                        className="secondary"
                        aria-label="Gestionar ubicaciones y estanterías"
                    >
                        📚 Gestionar Estanterías
                    </button>
                    <button
                        onClick={handleCreateUser}
                        className="primary"
                        aria-label="Crear nuevo usuario"
                    >
                        + Crear Usuario
                    </button>
                </div>
            </header>

            {loading ? (
                <div className="loading-state">
                    <p>Cargando usuarios...</p>
                </div>
            ) : users.length === 0 ? (
                <div className="empty-state">
                    <p>No hay usuarios registrados.</p>
                </div>
            ) : (
                <div className="users-table-container">
                    <table className="users-table" aria-label="Lista de usuarios">
                        <thead>
                        <tr>
                            <th scope="col">ID</th>
                            <th scope="col">Nombre</th>
                            <th scope="col">Email</th>
                            <th scope="col">Rol</th>
                            <th scope="col">Acciones</th>
                        </tr>
                        </thead>
                        <tbody>
                        {(users || []).map((user) => (
                            <tr key={user.id}>
                                <td>{user.id}</td>
                                <td>{user.nombre}</td>
                                <td>{user.email}</td>
                                <td>
                                        <span className={getRoleBadgeClass(user.rol || 'EMPLEADO')}>
                                            {user.rol || 'EMPLEADO'}
                                        </span>
                                </td>
                                <td className="actions-cell">
                                    <button
                                        onClick={() => handleEditUser(user)}
                                        className="action-btn edit"
                                        aria-label={`Editar usuario ${user.nombre}`}
                                    >
                                        ✏️ Editar
                                    </button>
                                    <button
                                        onClick={() => handleDeleteClick(user)}
                                        className="action-btn delete"
                                        disabled={user.id === 0 || user.email === currentUserEmail}
                                        aria-label={`Eliminar usuario ${user.nombre}`}
                                        title={user.id === 0 ? 'No se puede eliminar al usuario del sistema' :
                                            user.email === currentUserEmail ? 'No puedes eliminar tu propia cuenta' : ''}
                                    >
                                        🗑️ Eliminar
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {showUserForm && (
                <UserFormModal
                    user={editingUser}
                    onSuccess={handleUserFormSuccess}
                    onCancel={() => {
                        setShowUserForm(false);
                        setEditingUser(null);
                    }}
                />
            )}

            {showDeleteModal && deletingUser && (
                <DeleteUserModal
                    user={deletingUser}
                    onConfirm={handleDeleteConfirm}
                    loading={isDeleting}
                    onCancel={() => {
                        if (!isDeleting) {
                            setShowDeleteModal(false);
                            setDeletingUser(null);
                        }
                    }}
                />
            )}

            {showLocationModal && (
                <LocationManagementModal
                    onClose={() => setShowLocationModal(false)}
                    // We don't need to do anything on 'added' here, as the modal manages its own list
                    // and this page doesn't display locations in the main table.
                />
            )}
        </div>
    );
}
