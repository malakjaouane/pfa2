import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import api, { messageErreur } from '../api';
import { useI18n } from '../i18n/I18nContext';

/** Taille de page fixe : un tableau de lecture se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

const vide = { recherche: '', succes: '', du: '', au: '' };

/** Lit l'ensemble des critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => {
  const page = parseInt(params.get('page') ?? '0', 10);
  return {
    recherche: params.get('recherche') ?? '',
    succes: params.get('succes') ?? '',
    du: params.get('du') ?? '',
    au: params.get('au') ?? '',
    page: Number.isNaN(page) || page < 0 ? 0 : page
  };
};

/** Date et heure dans la langue active (l'ISO de l'API est locale : sans « Z »). */
const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

/**
 * Page « Historique des connexions » : journal des tentatives de
 * connexion (réussies ou échouées), traçabilité loi 09-08.
 *
 * Accès réservé aux administrateurs (UTILISATEUR_GERER, imposé par
 * SecurityConfig sur TOUTES les routes /api/historique-connexions) : le
 * journal expose e-mails et adresses IP. Recherche (e-mail, IP, nom),
 * filtre de résultat et période, pagination — puis suppression d'une
 * ligne ou purge totale avec confirmation. Tri fixe : du plus récent au
 * plus ancien. L'URL porte les critères : la vue filtrée se partage.
 */
export default function ConnexionsPage() {
  const { t, langue } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');
  const [info, setInfo] = useState('');

  const charger = async (f) => {
    setChargement(true);
    setErreur('');
    try {
      const params = new URLSearchParams();
      if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
      if (f.succes) params.set('succes', f.succes);
      if (f.du) params.set('du', f.du);
      if (f.au) params.set('au', f.au);
      params.set('page', String(f.page));
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/historique-connexions?${params.toString()}`);
      setLignes(res.data);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? res.data.length : compte);
    } catch (err) {
      setErreur(messageErreur(err));
    } finally {
      setChargement(false);
    }
  };

  // Toute modification de l'URL (appliquer, pagination, retour du
  // navigateur) recharge la liste avec les critères correspondants.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.succes) params.set('succes', f.succes);
    if (f.du) params.set('du', f.du);
    if (f.au) params.set('au', f.au);
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
  };

  const appliquerFiltres = (e) => {
    e.preventDefault();
    // Une nouvelle recherche repart à la première page.
    setSearchParams(construireParams(filtres), { replace: true });
  };

  const reinitialiser = () => {
    setFiltres({ ...vide, page: 0 });
    setSearchParams({}, { replace: true });
  };

  const allerPage = (page) => {
    setSearchParams(construireParams(filtres, { page }), { replace: true });
  };

  /**
   * Suppression d'une ligne (admin) : confirmation puis rechargement.
   * Si la ligne supprimée était la dernière de sa page, on revient à la
   * page précédente pour ne pas afficher une page vide sur un total > 0.
   */
  const demanderSuppression = (ev) => {
    const libelle = `${ev.email} — ${formaterDate(ev.dateConnexion, langue)}`;
    if (!window.confirm(t('connexions.supprimerConfirmer', { ligne: libelle }))) return;
    api.delete(`/historique-connexions/${ev.idConnexion}`)
      .then(() => {
        setErreur('');
        setInfo(t('connexions.supprimeOk'));
        if (lignes.length === 1 && filtres.page > 0) {
          allerPage(filtres.page - 1);
        } else {
          charger(filtres);
        }
      })
      .catch((err) => {
        setInfo('');
        setErreur(messageErreur(err));
      });
  };

  /**
   * Purge TOUT le journal (admin) : confirmation puis rechargement — on
   * revient à la première page si on était ailleurs.
   */
  const purgerTout = () => {
    if (!window.confirm(t('connexions.purgerConfirmer'))) return;
    api.delete('/historique-connexions')
      .then(() => {
        setErreur('');
        setInfo(t('connexions.purgeOk'));
        if (filtres.page > 0) {
          allerPage(0);
        } else {
          charger(filtres);
        }
      })
      .catch((err) => {
        setInfo('');
        setErreur(messageErreur(err));
      });
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(
    filtres.recherche.trim() || filtres.succes || filtres.du || filtres.au
  );

  const champRecherche = (e) => setFiltres((f) => ({ ...f, recherche: e.target.value }));
  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('connexions.titre')}</h2>
          <p>{t('connexions.sousTitre')}</p>
        </div>
        {total > 0 && (
          <button type="button" className="btn btn-danger" onClick={purgerTout}>
            {t('connexions.purger')}
          </button>
        )}
      </header>

      {erreur && <div className="alerte alerte-erreur">{erreur}</div>}
      {info && <div className="alerte alerte-succes">{info}</div>}

      <form className="carte filtre-bar" onSubmit={appliquerFiltres}>
        <div className="filtre-ligne">
          <input
            type="search"
            placeholder={t('connexions.recherchePlaceholder')}
            aria-label={t('connexions.recherchePlaceholder')}
            value={filtres.recherche}
            onChange={champRecherche}
          />
          <select aria-label={t('connexions.ariaResultat')} value={filtres.succes} onChange={champ('succes')}>
            <option value="">{t('connexions.tousResultats')}</option>
            <option value="true">{t('connexions.succesOption')}</option>
            <option value="false">{t('connexions.echecOption')}</option>
          </select>
        </div>
        <div className="filtre-ligne">
          <span className="filtre-periode-libelle">{t('connexions.periodeTitre')}</span>
          <input type="date" aria-label={t('connexions.periodeDu')} title={t('connexions.periodeDu')}
            value={filtres.du} onChange={champ('du')} />
          <span className="filtre-sep" aria-hidden="true">→</span>
          <input type="date" aria-label={t('connexions.periodeAu')} title={t('connexions.periodeAu')}
            value={filtres.au} onChange={champ('au')} />
          <small className="texte-muet filtre-aide">{t('connexions.periodeAide')}</small>
          <button type="submit" className="btn btn-primaire" disabled={chargement}>{t('connexions.appliquer')}</button>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>{t('connexions.reinitialiser')}</button>
        </div>
      </form>

      <div className="carte">
        <div className="vue-bar">
          <span className="texte-muet">
            {t('connexions.compteur', { de: depart, a: arrivee, total })}
            {filtresActifs && (
              <>
                {' · '}
                <button type="button" className="lien-bouton" onClick={reinitialiser}>
                  {t('connexions.reinitialiser')}
                </button>
              </>
            )}
          </span>
        </div>

        {chargement ? (
          <div className="center-page">{t('connexions.chargement')}</div>
        ) : lignes.length === 0 ? (
          <p className="texte-muet">{t('connexions.aucunResultat')}</p>
        ) : (
          <div className="table-enveloppe">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('connexions.colDate')}</th>
                  <th>{t('connexions.colUtilisateur')}</th>
                  <th>{t('connexions.colEmail')}</th>
                  <th>{t('connexions.colIp')}</th>
                  <th>{t('connexions.colResultat')}</th>
                  <th>{t('connexions.colMotif')}</th>
                  <th>{t('connexions.colActions')}</th>
                </tr>
              </thead>
              <tbody>
                {lignes.map((ev) => (
                  <tr key={ev.idConnexion}>
                    <td>{formaterDate(ev.dateConnexion, langue)}</td>
                    <td title={ev.utilisateur?.email ?? ''}>
                      {ev.utilisateur?.nomComplet ?? (
                        <span className="texte-muet">{t('connexions.compteSupprime')}</span>
                      )}
                    </td>
                    <td>{ev.email}</td>
                    <td>{ev.adresseIp}</td>
                    <td>
                      <span className={`badge ${ev.succes ? 'badge-vert' : 'badge-rouge'}`}>
                        {ev.succes ? t('connexions.resultatSucces') : t('connexions.resultatEchec')}
                      </span>
                    </td>
                    <td>{ev.motif ?? <span className="texte-muet">—</span>}</td>
                    <td>
                      <button
                        type="button"
                        className="btn btn-petit btn-danger"
                        onClick={() => demanderSuppression(ev)}
                      >
                        {t('connexions.supprimer')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {total > TAILLE_PAGE && (
          <nav className="pagination" aria-label={t('connexions.titre')}>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('connexions.pagePrecedente')}
            </button>
            <span className="texte-muet">{t('connexions.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}</span>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('connexions.pageSuivante')}
            </button>
          </nav>
        )}
      </div>
    </div>
  );
}
