import { useCallback, useEffect, useRef, useState } from 'react';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';
import { useAuth } from '../AuthContext';
import { IconCloche } from './Icons';
import ListeAlertes from './ListeAlertes';

/** Rythme du rafraîchissement : assez fréquent pour suivre le jour, assez
 *  lent pour ne pas marteler le serveur (les alertes sont recalculées à
 *  chaque appel — aucune table ne les stocke). */
const DELAI_MS = 60000;

/**
 * Cloche de notifications du compte connecté : compteur d'alertes du
 * portefeuille + panneau déroulant, rafraîchis toutes les minutes (et au
 * montage). Les alertes sont calculées côté serveur selon les permissions
 * du compte (demandes pour DEMANDE_EVALUER, budget pour BUDGET_CONSULTER).
 */
export default function NotificationsBell() {
  const { t } = useI18n();
  const { user } = useAuth();
  const [total, setTotal] = useState(0);
  const [alertes, setAlertes] = useState([]);
  const [ouvert, setOuvert] = useState(false);
  const [erreur, setErreur] = useState('');
  const minuteur = useRef(null);

  const charger = useCallback(async () => {
    // Compte en attente de changement de mot de passe : l'API répond 403 sur
    // toutes les routes (filtre V3) — on n'interroge même pas le serveur.
    if (user?.doitChangerMotDePasse) {
      setTotal(0);
      setAlertes([]);
      return;
    }
    try {
      const res = await api.get('/notifications');
      setTotal(res.data.total ?? 0);
      setAlertes(res.data.alertes ?? []);
    } catch (err) {
      // 401 : l'intercepteur gère l'expiration de session, on reste silencieux.
      // 403 « changement obligatoire » : l'écran dédié prend le relais.
      if (err.response?.status !== 401
        && err.response?.data?.code !== 'MOT_DE_PASSE_A_CHANGER') {
        setErreur(messageErreur(err));
      }
    }
  }, [user?.doitChangerMotDePasse]);

  useEffect(() => {
    charger();
    minuteur.current = setInterval(charger, DELAI_MS);
    return () => clearInterval(minuteur.current);
  }, [charger]);

  // Refermeture au clic extérieur (les contrôles de portée changent à
  // chaque rendu : on garde une ref stable).
  const racine = useRef(null);
  useEffect(() => {
    if (!ouvert) return undefined;
    const cliquerDehors = (e) => {
      if (racine.current && !racine.current.contains(e.target)) setOuvert(false);
    };
    const echap = (e) => { if (e.key === 'Escape') setOuvert(false); };
    document.addEventListener('mousedown', cliquerDehors);
    document.addEventListener('keydown', echap);
    return () => {
      document.removeEventListener('mousedown', cliquerDehors);
      document.removeEventListener('keydown', echap);
    };
  }, [ouvert]);

  return (
    <div className="notif-cloche" ref={racine}>
      <button
        type="button"
        className="notif-bouton"
        aria-haspopup="true"
        aria-expanded={ouvert}
        aria-label={t('suivi.notif.aria')}
        title={t('suivi.notif.titre')}
        onClick={() => setOuvert((o) => !o)}
      >
        <IconCloche size={18} />
        {total > 0 && <span className="notif-compteur">{total > 99 ? '99+' : total}</span>}
      </button>
      {ouvert && (
        <div className="notif-panneau" role="dialog" aria-label={t('suivi.notif.titre')}>
          <div className="notif-entete">
            <strong>{t('suivi.notif.titre')}</strong>
            <span className="texte-muet">{t('suivi.notif.compteur', { n: total })}</span>
          </div>
          {erreur && <div className="alerte alerte-erreur">{erreur}</div>}
          <div className="notif-corps">
            {alertes.length === 0
              ? <p className="texte-muet">{t('suivi.notif.vide')}</p>
              : <ListeAlertes alertes={alertes} />}
          </div>
        </div>
      )}
    </div>
  );
}
