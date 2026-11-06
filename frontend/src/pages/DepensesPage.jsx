import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api, { messageErreur, messageErreurBlob, telechargerFichier } from '../api';
import { useI18n } from '../i18n/I18nContext';

/** Taille de page fixe : un registre se feuillette, il ne s'épuise pas. */
const TAILLE_PAGE = 25;

const vide = { recherche: '', categorie: '', initiative: '', du: '', au: '' };

/** Lit les critères depuis l'URL (vue partageable, fil du navigateur). */
const lireDepuisUrl = (params) => ({
  recherche: params.get('recherche') ?? '',
  categorie: params.get('categorie') ?? '',
  initiative: params.get('initiative') ?? '',
  du: params.get('du') ?? '',
  au: params.get('au') ?? '',
  page: Math.max(0, parseInt(params.get('page') ?? '0', 10) || 0)
});

const formaterDate = (valeur, langue) =>
  new Date(valeur).toLocaleDateString(langue, { day: '2-digit', month: 'short', year: 'numeric' });

const formaterMontant = (valeur, langue) =>
  new Intl.NumberFormat(langue, { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 })
    .format(Number(valeur ?? 0));

/**
 * Registre global des dépenses : information financière — lecture ET
 * écriture réservées à DEPENSE_SAISIR (20), le serveur refuse aux autres
 * (la route protège aussi GET /api/initiatives/{id}/depenses).
 *
 * Filtres recherche / catégorie / initiative / période, total des dépenses
 * filtrées (en-tête X-Total-Montant du serveur), pagination et export CSV
 * complet. L'URL porte les critères : la vue filtrée se partage (« Voir
 * les dépenses » d'une fiche).
 */
export default function DepensesPage() {
  const { t, langue } = useI18n();
  const [searchParams, setSearchParams] = useSearchParams();

  const [filtres, setFiltres] = useState(() => lireDepuisUrl(searchParams));
  const [lignes, setLignes] = useState([]);
  const [total, setTotal] = useState(0);
  const [totalMontant, setTotalMontant] = useState(null);
  const [initiatives, setInitiatives] = useState([]);
  const [categories, setCategories] = useState([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState('');

  // Listes déroulantes (filtres) : initiatives + catégories déjà saisies.
  useEffect(() => {
    api.get('/initiatives?size=200')
      .then((res) => setInitiatives(res.data ?? []))
      .catch((err) => setErreur(messageErreur(err)));
    api.get('/depenses?size=200')
      .then((res) => {
        const uniques = [...new Set((res.data ?? []).map((d) => d.categorie).filter(Boolean))];
        setCategories(uniques.sort());
      })
      .catch(() => { /* la page de dépenses est rechargée juste après */ });
  }, []);

  // Toute modification de l'URL recharge le registre.
  useEffect(() => {
    const depuisUrl = lireDepuisUrl(searchParams);
    setFiltres(depuisUrl);
    charger(depuisUrl);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const construireParams = (f, options = {}) => {
    const params = new URLSearchParams();
    if (f.recherche.trim()) params.set('recherche', f.recherche.trim());
    if (f.categorie) params.set('categorie', f.categorie);
    if (f.initiative) params.set('initiative', f.initiative);
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
      const res = await api.get(`/depenses?${params.toString()}`);
      setLignes(res.data ?? []);
      const compte = parseInt(res.headers['x-total-count'] ?? '', 10);
      setTotal(Number.isNaN(compte) ? (res.data?.length ?? 0) : compte);
      const montant = res.headers['x-total-montant'];
      setTotalMontant(montant === undefined ? null : montant);
    } catch (err) {
      setErreur(messageErreur(err));
      setLignes([]);
      setTotalMontant(null);
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
        `/depenses/export?${construireParams(filtres).toString()}`,
        'depenses.csv'
      );
    } catch (err) {
      setErreur(await messageErreurBlob(err));
    }
  };

  const pagesTotales = Math.max(1, Math.ceil(total / TAILLE_PAGE));
  const depart = total === 0 ? 0 : filtres.page * TAILLE_PAGE + 1;
  const arrivee = Math.min((filtres.page + 1) * TAILLE_PAGE, total);
  const filtresActifs = Boolean(
    filtres.recherche.trim() || filtres.categorie || filtres.initiative || filtres.du || filtres.au
  );

  const champ = (nom) => (e) => setFiltres((f) => ({ ...f, [nom]: e.target.value }));

  return (
    <div className="page">
      <header className="page-entete">
        <div>
          <h2>{t('depenses.titre')}</h2>
          <p>{t('depenses.sousTitre')}</p>
        </div>
        <div className="actions">
          <button
            type="button"
            className="btn btn-secondaire"
            onClick={exporterCsv}
            disabled={lignes.length === 0}
            title={t('depenses.exportAide')}
          >
            {t('depenses.exportCsv')}
          </button>
        </div>
      </header>

      {erreur && <div className="alerte alerte-erreur">{erreur}</div>}

      <form className="carte filtre-bar" onSubmit={appliquerFiltres}>
        <div className="filtre-ligne">
          <input
            type="search"
            placeholder={t('depenses.recherche')}
            aria-label={t('depenses.recherche')}
            value={filtres.recherche}
            onChange={champ('recherche')}
          />
          <select aria-label={t('depenses.filtreCategorie')} value={filtres.categorie} onChange={champ('categorie')}>
            <option value="">{t('depenses.toutesCategories')}</option>
            {categories.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
          <select aria-label={t('depenses.colInitiative')} value={filtres.initiative} onChange={champ('initiative')}>
            <option value="">{t('depenses.toutesInitiatives')}</option>
            {initiatives.map((i) => (
              <option key={i.idInitiative} value={i.idInitiative}>{i.code}</option>
            ))}
          </select>
        </div>
        <div className="filtre-ligne">
          <span className="filtre-periode-libelle">{t('depenses.periodeDu')}</span>
          <input type="date" aria-label={t('depenses.periodeDu')} title={t('depenses.periodeDu')}
            value={filtres.du} onChange={champ('du')} />
          <span className="filtre-sep" aria-hidden="true">→</span>
          <input type="date" aria-label={t('depenses.periodeAu')} title={t('depenses.periodeAu')}
            value={filtres.au} onChange={champ('au')} />
          <button type="submit" className="btn btn-primaire" disabled={chargement}>
            {t('depenses.appliquer')}
          </button>
          <button type="button" className="btn btn-secondaire" onClick={reinitialiser}>
            {t('depenses.reinitialiser')}
          </button>
        </div>
      </form>

      <div className="carte">
        <div className="vue-bar">
          <span className="texte-muet">
            {total > 0 ? t('risques.compteur', { de: depart, a: arrivee, total }) : ''}
            {totalMontant !== null && (
              <strong className="depenses-total">
                {' · '}
                {t('depenses.total', { montant: formaterMontant(totalMontant, langue) })}
              </strong>
            )}
            {filtresActifs && (
              <>
                {' · '}
                <button type="button" className="lien-bouton" onClick={reinitialiser}>
                  {t('depenses.reinitialiser')}
                </button>
              </>
            )}
          </span>
        </div>

        {chargement ? (
          <div className="center-page">{t('common.chargement')}</div>
        ) : lignes.length === 0 ? (
          <p className="texte-muet">{t('depenses.aucune')}</p>
        ) : (
          <div className="table-enveloppe">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('depenses.colDate')}</th>
                  <th>{t('depenses.colInitiative')}</th>
                  <th>{t('depenses.colLibelle')}</th>
                  <th>{t('depenses.colMontant')}</th>
                  <th>{t('depenses.colCategorie')}</th>
                  <th>{t('depenses.colSaisisseur')}</th>
                </tr>
              </thead>
              <tbody>
                {lignes.map((d) => (
                  <tr key={d.idDepense}>
                    <td>{formaterDate(d.dateDepense, langue)}</td>
                    <td>
                      <Link to={`/initiatives/${d.idInitiative}`} className="table-lien-code">
                        {d.codeInitiative}
                      </Link>
                      <span className="texte-muet"> {d.nomInitiative}</span>
                    </td>
                    <td>{d.libelle}</td>
                    <td className="depenses-montant">{formaterMontant(d.montant, langue)}</td>
                    <td>{d.categorie ?? <span className="texte-muet">—</span>}</td>
                    <td title={d.saisisseur?.email ?? ''}>
                      {d.saisisseur?.nomComplet ?? <span className="texte-muet">—</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {total > TAILLE_PAGE && (
          <nav className="pagination" aria-label={t('depenses.titre')}>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page - 1)}
              disabled={filtres.page === 0 || chargement}
            >
              {t('depenses.pagePrecedente')}
            </button>
            <span className="texte-muet">
              {t('depenses.pageInfo', { courante: filtres.page + 1, total: pagesTotales })}
            </span>
            <button
              type="button"
              className="btn btn-petit btn-secondaire"
              onClick={() => allerPage(filtres.page + 1)}
              disabled={filtres.page + 1 >= pagesTotales || chargement}
            >
              {t('depenses.pageSuivante')}
            </button>
          </nav>
        )}
      </div>
    </div>
  );
}
