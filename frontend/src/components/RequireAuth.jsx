import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';

/**
 * Garde d'accès : authentification obligatoire + permissions éventuelles.
 */
export default function RequireAuth({ children, permissions = [] }) {
  const { user, loading } = useAuth();
  const { t } = useI18n();
  const location = useLocation();

  if (loading) {
    return <div className="center-page">{t('common.chargement')}</div>;
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Changement de mot de passe obligatoire (compte créé hors profil dev) :
  // la seule page autorisée est celle du changement, quel que soit le reste.
  if (user.doitChangerMotDePasse && location.pathname !== '/mot-de-passe') {
    return <Navigate to="/mot-de-passe" replace />;
  }

  const autorise = permissions.length === 0
    || permissions.some((p) => user.permissions?.includes(p));

  if (!autorise) {
    return <Navigate to="/dashboard" replace />;
  }

  return children ?? <Outlet />;
}
