import { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import api, { EVT_SESSION_EXPIREE } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  // Restaure la session au chargement de l'application.
  // L'authentification repose sur les cookies HttpOnly (pfa_access / pfa_refresh)
  // envoyés automatiquement par le navigateur : on appelle /auth/me directement,
  // sans jeton dans localStorage.
  useEffect(() => {
    api
      .get('/auth/me')
      .then((res) => {
        setUser(res.data);
        localStorage.setItem('pfa_user', JSON.stringify(res.data));
        // Premier acces : envoie vers l'assistant d'installation (2B5)
        if (localStorage.getItem('onboardingDone') !== '1' && window.location.pathname !== '/onboarding') {
          navigate('/onboarding', { replace: true });
        }
      })
      .catch(() => {
        localStorage.removeItem('pfa_user');
      })
      .finally(() => setLoading(false));
  }, []);

  // L'API a répondu 401 (jeton expiré, compte désactivé, permissions
  // révoquées…) : sans cela, l'application restait affichée comme connectée
  // alors que plus aucune requête ne passait.
  useEffect(() => {
    const deconnecter = () => setUser(null);
    window.addEventListener(EVT_SESSION_EXPIREE, deconnecter);
    return () => window.removeEventListener(EVT_SESSION_EXPIREE, deconnecter);
  }, []);

  const login = useCallback(async (email, motDePasse) => {
    const res = await api.post('/auth/login', { email, motDePasse });
    const data = res.data;
    localStorage.setItem('pfa_user', JSON.stringify(data));
    setUser(data);
    // Premier acces : envoie vers l'assistant d'installation (2B5)
    if (localStorage.getItem('onboardingDone') !== '1') {
      navigate('/onboarding', { replace: true });
    }
    return data;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('pfa_user');
    setUser(null);
  }, []);

  /**
   * Recharge le profil depuis /auth/me (dont le marqueur
   * « doitChangerMotDePasse ») : utilisé après un changement de mot de
   * passe pour lever l'obligation sans reconnexion.
   */
  const rafraichir = useCallback(async () => {
    const res = await api.get('/auth/me');
    setUser(res.data);
    localStorage.setItem('pfa_user', JSON.stringify(res.data));
    return res.data;
  }, []);

  /** Fonction courte : l'utilisateur possède-t-il telle permission ? */
  const hasPermission = useCallback(
    (permission) => Boolean(user?.permissions?.includes(permission)),
    [user]
  );

  /** L'utilisateur possède-t-il tel rôle ? */
  const hasRole = useCallback(
    (code) => user?.role?.code === code,
    [user]
  );

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, rafraichir, hasPermission, hasRole }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}