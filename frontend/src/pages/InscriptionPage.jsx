import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import LanguageSwitcher from '../components/LanguageSwitcher';
import MotifZellige from '../components/MotifZellige';

/**
 * Page d'inscription (POST /api/auth/signup).
 *
 * Crée une organisation + son premier compte administrateur. Le compte est
 * créé avec l'adresse e-mail non vérifiée : un e-mail de vérification est
 * envoyé et le compte ne peut pas se connecter tant qu'il n'est pas confirmé.
 */
export default function InscriptionPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({
    nom: '', prenom: '', email: '', motDePasse: '', nomOrganisation: ''
  });
  const [succes, setSucces] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const [afficherMdp, setAfficherMdp] = useState(false);

  const set = (champ) => (e) => setForm((f) => ({ ...f, [champ]: e.target.value }));

  const gererSoumission = async (e) => {
    e.preventDefault();
    setErreur('');
    setSucces(false);

    if (form.motDePasse.length < 8) {
      setErreur(t('inscription.regle'));
      return;
    }
    if (form.motDePasse.toLowerCase() === form.email.toLowerCase().trim()) {
      setErreur(t('inscription.regle'));
      return;
    }

    setEnvoi(true);
    try {
      await api.post('/api/auth/signup', {
        email: form.email.trim().toLowerCase(),
        motDePasse: form.motDePasse,
        nom: form.nom.trim(),
        prenom: form.prenom.trim(),
        nomOrganisation: form.nomOrganisation.trim()
      });
      setSucces(true);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  if (succes) {
    return (
      <div className="login-page">
        <MotifZellige className="motif-fond-login" />
        <div className="login-boite">
          <div className="login-haut">
            <Link to="/login" className="login-retour">← {t('inscription.seConnecter')}</Link>
            <LanguageSwitcher />
          </div>
          <div className="login-entete">
            <h1>{t('inscription.titre')}</h1>
            <p>{t('inscription.sousTitre')}</p>
          </div>
          <div className="alerte alerte-info">{t('inscription.succes')}</div>
          <button
            type="button"
            className="btn btn-primaire btn-block"
            onClick={() => navigate(location.state?.from?.pathname ?? '/login', { replace: true })}
          >
            {t('inscription.seConnecter')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="login-page">
      <MotifZellige className="motif-fond-login" />
      <div className="login-boite">
        <div className="login-haut">
          <Link to="/login" className="login-retour">← {t('login.retour')}</Link>
          <LanguageSwitcher />
        </div>
        <div className="login-entete">
          <h1>{t('inscription.titre')}</h1>
          <p>{t('inscription.sousTitre')}</p>
        </div>

        <form className="form" onSubmit={gererSoumission}>
          {erreur && <div className="alerte alerte-erreur">{erreur}</div>}
          <div className="champ">
            <label>{t('inscription.prenom')}</label>
            <input type="text" name="prenom" value={form.prenom} onChange={set('prenom')} placeholder={t('inscription.prenom')} required />
          </div>
          <div className="champ">
            <label>{t('inscription.nom')}</label>
            <input type="text" name="nom" value={form.nom} onChange={set('nom')} placeholder={t('inscription.nom')} required />
          </div>
          <div className="champ">
            <label>{t('inscription.email')}</label>
            <input type="email" name="email" value={form.email} onChange={set('email')} placeholder="email@exemple.com" autoComplete="username" required />
          </div>
          <div className="champ">
            <label>{t('inscription.nomOrganisation')}</label>
            <input type="text" name="nomOrganisation" value={form.nomOrganisation} onChange={set('nomOrganisation')} placeholder="Ma organisation" required />
          </div>
          <div className="champ">
            <label>{t('inscription.motDePasse')}</label>
            <input
              type={afficherMdp ? 'text' : 'password'}
              name="motDePasse"
              value={form.motDePasse}
              onChange={set('motDePasse')}
              placeholder="••••••••"
              autoComplete="new-password"
              required
            />
            <span className="aide-champ">{t('inscription.regle')}</span>
          </div>
          <label className="case">
            <input type="checkbox" checked={afficherMdp} onChange={(e) => setAfficherMdp(e.target.checked)} />
            {t('login.afficherMdp')}
          </label>
          <button className="btn btn-primaire btn-block" type="submit" disabled={envoi}>
            {envoi ? t('inscription.enCours') : t('inscription.creer')}
          </button>
          <div className="alerte alerte-info">
            {t('inscription.dejaCompte')} <Link to="/login">{t('inscription.seConnecter')}</Link>
          </div>
        </form>
      </div>
    </div>
  );
}
