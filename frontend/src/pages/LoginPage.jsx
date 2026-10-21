import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { useI18n } from '../i18n/I18nContext';
import LanguageSwitcher from '../components/LanguageSwitcher';
import MotifZellige from '../components/MotifZellige';
import { messageErreur } from '../api';

/**
 * Page de connexion (POST /api/auth/login).
 */
export default function LoginPage() {
  const { login, user } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [motDePasse, setMotDePasse] = useState('');
  const [afficherMdp, setAfficherMdp] = useState(false);
  const [erreur, setErreur] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [motDePasseOublie, setMotDePasseOublie] = useState(false);

  if (user) {
    navigate('/dashboard', { replace: true });
  }

  const gererSoumission = async (e) => {
    e.preventDefault();
    setErreur('');

    // On lit directement les champs du formulaire (et non uniquement l'état
    // React) : le gestionnaire de mots de passe du navigateur peut remplir
    // les champs sans déclencher onChange, laissant l'état obsolète et
    // envoyant ainsi un mot de passe erroné. L'email est en outre débarrassé
    // des espaces parasites.
    const form = e.currentTarget;
    const emailSaisi = (form.elements.email?.value ?? email).trim();
    const mdpSaisi = form.elements.motDePasse?.value ?? motDePasse;
    setEmail(emailSaisi);

    setEnvoi(true);
    try {
      await login(emailSaisi, mdpSaisi);
      navigate(location.state?.from?.pathname ?? '/dashboard', { replace: true });
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <div className="login-page">
      <MotifZellige className="motif-fond-login" />
      <div className="login-boite">
        <div className="login-haut">
          <Link to="/" className="login-retour">← {t('login.retour')}</Link>
          <LanguageSwitcher />
        </div>
        <div className="login-entete">
          <img
            className="brand-logo brand-logo-large"
            src="/images/logo-it6.png"
            alt="IT6 Academy"
            width="132"
            height="138"
          />
          <h1>IT6 Consulting</h1>
          <p>{t('login.sousTitre')}</p>
          <p className="login-note">{t('login.compteCree')}</p>
        </div>

        <form className="form" onSubmit={gererSoumission}>
          {erreur && <div className="alerte alerte-erreur">{erreur}</div>}
          <div className="champ">
            <label>{t('login.email')}</label>
            <input
              type="email"
              name="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="email@it6.ma"
              autoComplete="username"
              required
            />
          </div>
          <div className="champ">
            <label>{t('login.motDePasse')}</label>
            <input
              type={afficherMdp ? 'text' : 'password'}
              name="motDePasse"
              value={motDePasse}
              onChange={(e) => setMotDePasse(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
              required
            />
          </div>
          <label className="case">
            <input
              type="checkbox"
              checked={afficherMdp}
              onChange={(e) => setAfficherMdp(e.target.checked)}
            />
            {t('login.afficherMdp')}
          </label>
          <button className="btn btn-primaire btn-block" type="submit" disabled={envoi}>
            {envoi ? t('login.connexion') : t('login.seConnecter')}
          </button>
          <button
            type="button"
            className="login-lien"
            onClick={() => setMotDePasseOublie((v) => !v)}
            aria-expanded={motDePasseOublie}
          >
            {t('login.oublieLien')}
          </button>
          {motDePasseOublie && <div className="alerte alerte-info">{t('login.oublie')}</div>}
        </form>
      </div>
    </div>
  );
}
