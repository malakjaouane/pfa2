import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../i18n/I18nContext';
import { afficherMessage } from '../i18n/message';
import api, { messageErreur } from '../api';
import { useAuth } from '../AuthContext';

/**
 * Écran « Changer mon mot de passe » (utilisateur déjà connecté).
 *
 * <p>POST /api/auth/changer-mot-de-passe : le backend vérifie le mot de
 * passe actuel, applique la politique (8 caractères, lettre + chiffre,
 * différent de l'email) puis ré-hash le nouveau mot de passe en BCrypt.</p>
 *
 * <p>Compte marqué « changement obligatoire » (compte admin créé hors
 * profil dev) : un bandeau l'annonce, et une fois le nouveau mot de passe
 * enregistré le profil est rechargé (/auth/me) puis l'utilisateur est
 * redirigé vers le tableau de bord — l'obligation est levée.</p>
 */
export default function MotDePassePage() {
  const { t } = useI18n();
  const { user, rafraichir } = useAuth();
  const navigate = useNavigate();
  const [actuel, setActuel] = useState('');
  const [nouveau, setNouveau] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [afficher, setAfficher] = useState(false);
  const [erreur, setErreur] = useState('');
  const [succes, setSucces] = useState('');
  const [envoi, setEnvoi] = useState(false);

  const obligation = Boolean(user?.doitChangerMotDePasse);

  const soumettre = async (e) => {
    e.preventDefault();
    setErreur('');
    setSucces('');

    if (nouveau !== confirmation) {
      setErreur({ cle: 'motDePasse.erreurConfirmation' });
      return;
    }

    setEnvoi(true);
    try {
      await api.post('/auth/changer-mot-de-passe', {
        motDePasseActuel: actuel,
        nouveauMotDePasse: nouveau
      });
      setSucces(t('motDePasse.succes'));
      setActuel('');
      setNouveau('');
      setConfirmation('');
      if (obligation) {
        // Le marqueur est levé côté base : on recharge le profil puis on
        // quitte l'écran imposé (RequireAuth ne bloque plus l'accès).
        await rafraichir();
        navigate('/dashboard', { replace: true });
      }
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('motDePasse.titre')}</h2>
          <p>{t('motDePasse.sousTitre')}</p>
        </div>
      </header>

      {obligation && (
        <div className="alerte alerte-info" role="alert">
          {t('motDePasse.obligatoire')}
        </div>
      )}
      {succes && <div className="alerte alerte-succes">{succes}</div>}
      {erreur && <div className="alerte alerte-erreur">{afficherMessage(t, erreur)}</div>}

      <div className="carte carte-formulaire">
        <form className="formulaire" onSubmit={soumettre}>
          <div className="champ">
            <label>{t('motDePasse.actuel')}</label>
            <input
              type={afficher ? 'text' : 'password'}
              value={actuel}
              onChange={(e) => setActuel(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
          <div className="champ">
            <label>{t('motDePasse.nouveau')}</label>
            <input
              type={afficher ? 'text' : 'password'}
              value={nouveau}
              onChange={(e) => setNouveau(e.target.value)}
              autoComplete="new-password"
              required
            />
          </div>
          <div className="champ">
            <label>{t('motDePasse.confirmation')}</label>
            <input
              type={afficher ? 'text' : 'password'}
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
              autoComplete="new-password"
              required
            />
          </div>
          <label className="case">
            <input
              type="checkbox"
              checked={afficher}
              onChange={(e) => setAfficher(e.target.checked)}
            />
            {t('motDePasse.afficher')}
          </label>
          <p className="texte-muet">{t('motDePasse.regle')}</p>
          <button className="btn btn-primaire" type="submit" disabled={envoi}>
            {envoi ? t('motDePasse.enCours') : t('motDePasse.enregistrer')}
          </button>
        </form>
      </div>
    </div>
  );
}
