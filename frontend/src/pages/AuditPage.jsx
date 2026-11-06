import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useI18n } from '../i18n/I18nContext';

/** Taille de page fixe : un registre se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

/** Méthodes enregistrées — le serveur rejette tout autre filtre (400). */
const METHODES = ['POST', 'PUT', 'PATCH', 'DELETE'];

const vide = { email: '', methode: '', route: '', du: '', au: '' };

/** Lit les critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => ({
  email: params.get('email') ?? '',
  methode: METHODES.includes(params.get('methode')) ? params.get('methode') : '',
  route: params.get('route') ?? '',
  du: params.get('du') ?? '',
  au: params.get('au') ?? '',
  page: Math.max(0, parseInt(params.get('page') ?? '0', 10) || 0)
});

/** Date et heure dans la langue active (l'ISO de l'API est locale : sans « Z »). */
const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleString(langue, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });

/** Badge de statut HTTP : 2xx vert, 4xx orange, 5xx rouge. */
function badgeStatut(code) {
  if (code >= 200 && code < 300) return 'badge-vert';
  if (code >= 500) return 'badge-rouge';
  return 'badge-orange';
}

/**
 * Journal d'audit : qui a fait QUOI depuis OÙ. Lecture seule — le journal
 * est immuable (aucune API de suppression ne sait l'éditer) et l'accès est
 * réservé à UTILISATEUR_GERER, donc aux comptes qui gèrent justement les
 * utilisateurs.
 *
 * Filtres e-mail / méthode / route / période, pagination et export CSV
 * complet. L'URL porte les critères : la vue filtrée se partage.
 */
export default function AuditPage() {
  const { t, langue } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');

  // Toute modification de l'URL recharge le journal.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.email.trim()) params.set('email', f.email.trim());
    if (f.methode) params.set('methode', f.methode);
    if (f.route.trim()) params.set('route', f.route.trim());
    if (f.du) params.set('du', f.du);
    if (f.au) params.set('au', f.au);
    const page = options.page ?? 0;
    if (page > 0) params.set('page', String(page));
    return params;
  };

  const charger = async (f) => {
    setChargement(true);
    setErreur('');
    try {
      const params = construireParams(f, { page: f.page });
      params.set('size', String(TAILLE_PAGE));
      const res = await api.get(`/journal-audit?${params.toString()}`);
      setLignes(res.data ?? []);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? (res.data?.length ?? 0) : compte);
    } catch (err) {
      setErreur(messageErreur(err));
      setLignes([]);
    } finally {
      setChargement(false);
    }
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

  const exporterCsv = async () => {
    setErreur('');
    try {
      await telechargerFichier(
        `/journal-audit/export?${construireParams(filtres).toString()}`,
        'journal-audit.csv'
      );
    } catch (err) {
      setErreur(await messageErreurBlob(err));
    }
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(
    filtres.email.trim() || filtres.methode || filtres.route.trim() || filtres.du || filtres.au
  );

  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('audit.titre')}</h2>
          <p>{t('audit.sousTitre')}</p>
        </div>
        <div className="actions">
          <button
            type="button"
            className="btn btn-secondaire"
            onClick={exporterCsv}
            disabled={lignes.length === 0}
            title={t('audit.exportAide')}
          >
            {t('audit.exportCsv')}
          </button>
        </div>
      </header>

      {erreur && <div className="alerte alerte-erreur">{erreur}</div>}

      <form className="carte filtre-bar" onSubmit={appliquerFiltres}>
        <div className="filtre-ligne">
          <input
            type="search"
            placeholder={t('audit.emailPlaceholder')}
            aria-label={t('audit.email')}
            value={filtres.email}
            onChange={champ('email')}
          />
          <select aria-label={t('audit.methode')} value={filtres.methode} onChange={champ('methode')}>
            <option value="">{t('audit.toutesMethodes')}</option>
            {METHODES.map((m) => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
          <input
            type="search"
            placeholder={t('audit.routePlaceholder')}
            aria-label={t('audit.route')}
            value={filtres.route}
            onChange={champ('route')}
          />
          <span className="filtre-periode-libelle">{t('audit.periodeDu')}</span>
          <input type="date" aria-label={t('audit.periodeDu')} title={t('audit.periodeDu')}
            value={filtres.du} onChange={champ('du')} />
          <span className="filtre-sep" aria-hidden="true">→</span>
          <input type="date" aria-label={t('audit.periodeAu')} title={t('audit.periodeAu')}
            value={filtres.au} onChange={champ('au')} />
          <button type="submit" className="btn btn-primaire" disabled={chargement}>
            {t('audit.appliquer')}
          </button>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>
            {t('audit.reinitialiser')}
          </button>
        </div>
      </form>

      <div className="carte">
        <div className="vue-bar">
          <span className="texte-muet">
            {total > 0 ? t('risques.compteur', { de: depart, a: arrivee, total }) : ''}
            {filtresActifs && (
              <>
                {' · '}
                <button type="button" className="lien-bouton" onClick={reinitialiser}>
                  {t('audit.reinitialiser')}
                </button>
              </>
            )}
          </span>
        </div>

        {chargement ? (
          <div className="center-page">{t('common.chargement')}</div>
        ) : lignes.length === 0 ? (
          <p className="texte-muet">{t('audit.aucun')}</p>
        ) : (
          <div className="table-enveloppe">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('audit.colDate')}</th>
                  <th>{t('audit.colEmail')}</th>
                  <th>{t('audit.colMethode')}</th>
                  <th>{t('audit.colRoute')}</th>
                  <th>{t('audit.colStatut')}</th>
                  <th>{t('audit.colIp')}</th>
                  <th>{t('audit.colCorps')}</th>
                </tr>
              </thead>
              <tbody>
                {lignes.map((l) => (
                  <tr key={l.idAudit ?? `${l.dateAction}-${l.route}`}>
                    <td>{formaterDate(l.dateAction, langue)}</td>
                    <td title={l.email}>
                      {l.email || <span className="texte-muet">{t('audit.horsApi')}</span>}
                    </td>
                    <td><span className="badge badge-bleu">{l.methode}</span></td>
                    <td>
                      {l.idInitiative ? (
                        <Link to={`/initiatives/${l.idInitiative}`} className="table-lien-code">
                          {l.route}
                        </Link>
                      ) : (
                        l.route
                      )}
                    </td>
                    <td><span className={`badge ${badgeStatut(l.statutHttp)}`}>{l.statutHttp}</span></td>
                    <td>{l.ipAdresse ?? <span className="texte-muet">—</span>}</td>
                    <td><code className="audit-corps">{l.corps || '—'}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {total > TAILLE_PAGE && (
          <nav className="pagination" aria-label={t('audit.titre')}>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('audit.pagePrecedente')}
            </button>
            <span className="texte-muet">
              {t('audit.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}
            </span>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('audit.pageSuivante')}
            </button>
          </nav>
        )}
      </div>
    </div>
  );
}